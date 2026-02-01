package com.c.domain.activity.service.rule.armory;

import com.c.domain.activity.model.entity.ActivitySkuEntity;
import com.c.domain.activity.repositor.IActivityRepository;
import com.c.types.common.Constants;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Date;

/**
 * 活动装配预热与调度执行服务
 * 职责：
 * 1. 活动装配（Armory）：活动上线前的“预热桩”。将 DB 中的静态规则、库存配置、活动元数据强刷至 Redis，
 * 通过数据前置（Data Pre-loading）消除高并发下的数据库 I/O 瓶颈。
 * 2. 活动调度（Dispatch）：运行时的“流量闸口”。利用 Redis 原子性操作处理高频库存扣减，
 * 作为系统抗压的第一道防线，确保存储层的最终一致性。
 *
 * @author cyh
 * @date 2026/01/31
 */
@Service
public class ActivityArmory implements IActivityArmory, IActivityDispatch {

    @Resource
    private IActivityRepository activityRepository;

    /**
     * 装配活动 SKU 全量配置数据
     * 核心逻辑：
     * 1. 完整性校验：查询 SKU 基础配置，确保关联的活动与次数规则存在。
     * 2. 库存预热：将物理库存映射为 Redis 原子计数器，这是后续所有高并发扣减的基准。
     * 3. 关联预热：利用 Repository 的缓存逻辑，同步预热活动信息（Activity）与参与频次（Count）规则。
     *
     * @param sku 活动商品库存单元标识
     * @return true-装配成功（所有核心要素已就绪）；false-数据异常，装配终止
     */
    @Override
    public boolean assembleActivitySku(Long sku) {
        // 1. 获取 SKU 配置快照：若 SKU 不存在，后续装配无意义
        ActivitySkuEntity activitySkuEntity = activityRepository.queryActivitySku(sku);
        if (null == activitySkuEntity) return false;

        // 2. 核心库存预热：将 DB 剩余库存同步至 Redis 原子计数器
        cacheActivitySkuStockCount(sku, activitySkuEntity.getStockCount());

        // 3. 活动元数据预热：加载活动基本信息（名称、策略 ID、状态等）至缓存
        activityRepository.queryRaffleActivityByActivityId(activitySkuEntity.getActivityId());

        // 4. 参与限制规则预热：加载频次控制规则（总/日/月限制）至缓存
        activityRepository.queryRaffleActivityCountByActivityCountId(activitySkuEntity.getActivityCountId());

        return true;
    }

    /**
     * 缓存 SKU 库存计数器
     * 技术细节：
     * 1. 拼接全局统一的库存缓存 Key，保证 Redis 空间命名规范。
     * 2. 调用底层的 setAtomicLong，需具备幂等性（若已存在且未重置，则不覆盖正在变动中的运行值）。
     *
     * @param sku        SKU 唯一标识
     * @param stockCount 待预热的初始库存值
     */
    private void cacheActivitySkuStockCount(Long sku, Integer stockCount) {
        String cacheKey = Constants.RedisKey.ACTIVITY_SKU_STOCK_COUNT_KEY + sku;
        activityRepository.cacheActivitySkuStockCount(cacheKey, stockCount);
    }

    /**
     * 调度执行：原子扣减活动 SKU 库存
     * 核心设计：
     * 1. 原子操作：利用 Redis 的 DECR 指令保障并发安全，防止超卖。
     * 2. 状态感知：当库存触达临界点（0）时，由底层 Repository 自动触发 MQ 售罄事件，实现异步闭环。
     * 3. 生命周期：传入活动结束时间，便于后续在底层进行 Key 的 TTL（生存时间）管理，防止 Redis 内存泄露。
     *
     * @param sku         SKU 唯一标识
     * @param endDateTime 活动结束时间（用于缓存生命周期控制）
     * @return true-扣减成功（库存充足）；false-扣减失败（售罄或服务异常）
     */
    @Override
    public boolean subtractionActivitySkuStock(Long sku, Date endDateTime) {
        // 1. 构建库存操作唯一 Key
        String cacheKey = Constants.RedisKey.ACTIVITY_SKU_STOCK_COUNT_KEY + sku;

        // 2. 代理至仓储层执行原子扣减逻辑
        // 注意：此处仅关注扣减结果，具体的负值补偿、售罄消息发布已在 Repository 内部封装实现
        return activityRepository.subtractionActivitySkuStock(sku, cacheKey, endDateTime);
    }
}