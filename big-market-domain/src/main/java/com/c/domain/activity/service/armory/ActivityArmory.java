package com.c.domain.activity.service.armory;

import com.c.domain.activity.model.entity.ActivitySkuEntity;
import com.c.domain.activity.repositor.IActivityRepository;
import com.c.types.common.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;

/**
 * 活动军械库服务实现类 (Activity Armory Service)
 * 1. 活动装配 (Assemble)：在活动开始前，将分散在数据库中的配置（SKU、活动信息、额度规则）进行整合并推送到缓存。
 * 2. 库存预热：将物理库的库存水位同步到 Redis 原子计数器中，支撑高并发扣减。
 * 3. 调度分发 (Dispatch)：提供高性能的缓存库存操作接口，实现扣减逻辑与物理库的异步解耦。
 *
 * @author cyh
 * @date 2026/02/01
 */
@Slf4j
@Service
public class ActivityArmory implements IActivityArmory, IActivityDispatch {

    @Resource
    private IActivityRepository activityRepository;

    /**
     * 装配活动 SKU 配置并执行缓存预热
     * 1. 检索 SKU 基本信息：获取该商品对应的库存总量、关联活动 ID 以及限额规则 ID。
     * 2. 预热库存：利用 Redis AtomicLong 结构初始化当前剩余库存。
     * 3. 级联预热：通过“查询即预热”机制，将活动主表配置及次数限制配置同步到分布式缓存中。
     *
     * @param sku 活动商品库存单元唯一标识
     * @return boolean 装配是否成功
     */
    @Override
    public boolean assembleActivitySku(Long sku) {
        // 1. 查询 SKU 基础信息，包含关联的活动 ID 和次数规则 ID
        ActivitySkuEntity activitySkuEntity = activityRepository.queryActivitySku(sku);
        if (null == activitySkuEntity) {
            log.error("活动 SKU 装配失败，未找到该 SKU 记录: {}", sku);
            return false;
        }

        // 2. 核心：预热活动 SKU 缓存库存（支撑高性能扣减的关键步骤）
        cacheActivitySkuStockCount(sku, activitySkuEntity.getStockCountSurplus());

        // 3. 预热活动主体配置：调用仓储层查询，其内部逻辑会自动将 PO 转化为 Entity 并存入 Redis
        activityRepository.queryRaffleActivityByActivityId(activitySkuEntity.getActivityId());

        // 4. 预热活动次数额度限制规则：确保下单校验阶段无需回表查询
        activityRepository.queryRaffleActivityCountByActivityCountId(activitySkuEntity.getActivityCountId());

        log.info("活动 SKU 装配完成: sku={}, activityId={}", sku, activitySkuEntity.getActivityId());
        return true;
    }

    @Override
    public boolean assembleActivitySkuByActivityId(Long activityId) {

        List<ActivitySkuEntity> activitySkuEntities =
                activityRepository.queryActivitySkuListByActivityId(activityId);

        for (ActivitySkuEntity activitySkuEntity : activitySkuEntities) {
            cacheActivitySkuStockCount(activitySkuEntity.getSku(), activitySkuEntity.getStockCountSurplus());
            // 预热活动次数【查询时预热到缓存】
            activityRepository.queryRaffleActivityCountByActivityCountId(activitySkuEntity.getActivityCountId());
        }

        // 预热活动【查询时预热到缓存】
        activityRepository.queryRaffleActivityByActivityId(activityId);

        return true;
    }

    /**
     * 内部方法：缓存活动 SKU 库存数量
     *
     * @param sku        商品 SKU 编号
     * @param stockCount 待预热的库存总量（来自数据库剩余库存）
     */
    private void cacheActivitySkuStockCount(Long sku, Integer stockCount) {
        // 构建标准缓存键：activity_sku_stock_count_key:{sku}
        String cacheKey = Constants.RedisKey.ACTIVITY_SKU_STOCK_COUNT_KEY + sku;
        activityRepository.cacheActivitySkuStockCount(cacheKey, stockCount);
    }

    /**
     * 调度执行：原子扣减活动 SKU 缓存库存
     * 采用 Redis 预扣减方案。活动期间，所有库存扣减请求均在缓存层闭环处理，
     * 扣减成功后通过异步流水任务同步回数据库，从而解决数据库行锁争抢导致的性能瓶颈。
     *
     * @param sku         商品 SKU 编号
     * @param endDateTime 活动结束时间（用于控制缓存 Key 的生命周期，防止僵尸数据堆积）
     * @return boolean    true-扣减成功（有余量）；false-扣减失败（售罄或活动结束）
     */
    @Override
    public boolean subtractionActivitySkuStock(Long sku, Date endDateTime) {
        String cacheKey = Constants.RedisKey.ACTIVITY_SKU_STOCK_COUNT_KEY + sku;
        return activityRepository.subtractionActivitySkuStock(sku, cacheKey, endDateTime);
    }

}