package com.c.domain.activity.service.rule.armory;

import com.c.domain.activity.model.entity.ActivitySkuEntity;
import com.c.domain.activity.repositor.IActivityRepository;
import com.c.types.common.Constants;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Date;

/**
 * @description 活动装配库（军火库）与调度服务
 * 职责：
 * 1. 装配（Armory）：负责将数据库中的活动配置、库存、策略等数据预热到 Redis 缓存中。
 * 2. 调度（Dispatch）：负责线上活动过程中的库存扣减等高频操作。
 */
@Service
public class ActivityArmory implements IActivityArmory, IActivityDispatch {

    @Resource
    private IActivityRepository activityRepository;

    /**
     * 装配活动 SKU 相关配置数据到缓存
     * @param sku 活动商品库存单元 ID
     * @return 装配结果
     */
    @Override
    public boolean assembleActivitySku(Long sku) {
        // 1. 查询 SKU 基础信息
        ActivitySkuEntity activitySkuEntity = activityRepository.queryActivitySku(sku);
        if (null == activitySkuEntity) return false;

        // 2. 预热库存：将活动 SKU 的初始库存同步至 Redis，后续扣减均在缓存进行
        cacheActivitySkuStockCount(sku, activitySkuEntity.getStockCount());

        // 3. 预热活动基本信息：利用 Repository 的缓存机制，将活动主体数据加载到 Redis
        activityRepository.queryRaffleActivityByActivityId(activitySkuEntity.getActivityId());

        // 4. 预热活动参与次数配置：将关联的次数限制规则同步至缓存
        activityRepository.queryRaffleActivityCountByActivityCountId(activitySkuEntity.getActivityCountId());

        return true;
    }

    /**
     * 缓存 SKU 库存数据
     * @param sku        商品 SKU
     * @param stockCount 初始库存数量
     */
    private void cacheActivitySkuStockCount(Long sku, Integer stockCount) {
        String cacheKey = Constants.RedisKey.ACTIVITY_SKU_STOCK_COUNT_KEY + sku;
        activityRepository.cacheActivitySkuStockCount(cacheKey, stockCount);
    }

    /**
     * 扣减活动 SKU 库存（调度执行）
     * @param sku         商品 SKU
     * @param endDateTime 活动结束时间（用于设置缓存 Key 的过期时间，防止死数据）
     * @return 扣减是否成功（true: 扣减成功，仍有库存；false: 库存不足或扣减失败）
     */
        @Override
        public boolean subtractionActivitySkuStock(Long sku, Date endDateTime) {
            String cacheKey = Constants.RedisKey.ACTIVITY_SKU_STOCK_COUNT_KEY + sku;
            // 调用仓储层执行具体的原子扣减逻辑（通常是 Redis 的 decr 操作）
            return activityRepository.subtractionActivitySkuStock(sku, cacheKey, endDateTime);
    }
}