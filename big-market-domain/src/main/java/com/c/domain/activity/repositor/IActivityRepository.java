package com.c.domain.activity.repositor;

import com.c.domain.activity.model.entity.ActivityCountEntity;
import com.c.domain.activity.model.entity.ActivityEntity;
import com.c.domain.activity.model.entity.ActivitySkuEntity;

/**
 * @author cyh
 * @description 抽奖活动仓储接口
 * 1. 定义了活动领域层对持久化数据的操作契约。
 * 2. 遵循 DDD 规范，接口定义在领域层（Domain），具体实现在基础设施层（Infrastructure）。
 * @date 2026/01/27
 */
public interface IActivityRepository {

    /**
     * 根据 SKU 编码查询活动库存单元（Activity SKU）
     * SKU 是活动领取的最小单元，通过该查询可以获取关联的 activityId 和 activityCountId。
     *
     * @param sku 外部传入的商品或服务 SKU 编号
     * @return ActivitySkuEntity 活动 SKU 实体，包含库存、状态及关联 ID
     */
    ActivitySkuEntity queryActivitySku(Long sku);

    /**
     * 根据活动 ID 查询抽奖活动的基础配置信息
     * 用于获取活动的策略 ID、活动名称、起止时间及当前活动状态等核心信息。
     *
     * @param activityId 活动唯一标识 ID
     * @return ActivityEntity 抽奖活动实体
     */
    ActivityEntity queryRaffleActivityByActivityId(Long activityId);

    /**
     * 根据次数配置 ID 查询抽奖活动可参与次数限制
     * 包含用户在活动期间的总次数、日次数、月次数等阈值配置。
     *
     * @param activityCountId 次数配置项 ID
     * @return ActivityCountEntity 活动次数限制实体
     */
    ActivityCountEntity queryRaffleActivityCountByActivityCountId(Long activityCountId);

}