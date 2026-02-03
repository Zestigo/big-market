package com.c.domain.activity.service.armory;

/**
 * 活动军械库接口 (Activity Armory Interface)
 * 1. 活动战备 (Preparation)：在活动正式开始前，将复杂的活动 SKU 配置从持久层（MySQL）装配到高速缓存层（Redis）。
 * 2. 性能保障：通过预热库存计数器、活动配置快照，确保高并发流量涌入时，系统能够实现 O(1) 级别的快速响应。
 * 3. 状态前置：将数据库中的物理状态同步至缓存，实现业务逻辑在内存中的闭环计算。
 *
 * @author cyh
 * @date 2026/02/01
 */
public interface IActivityArmory {

    /**
     * 装配活动 SKU 配置并预热缓存数据
     * 1. 基础配置预热：将 sku 关联的活动信息 (Activity)、次数规则 (Count) 写入缓存。
     * 2. 库存流水同步：将数据库中的 `stock_count_surplus`（剩余库存）初始化到 Redis 原子计数器。
     * 3. 售罄标识复位：确保新装配的 SKU 不会误触之前的熔断标识。
     *
     *
     *
     * @param sku 活动商品库存单元唯一标识。作为装配的“总抓手”，通过它可以索引到活动主体、次数策略及策略规则。
     * @return boolean 装配结果反馈：
     * - true：装配成功，该 SKU 已具备对外提供抽奖服务的能力。
     * - false：装配失败，通常由于数据不完整、数据库异常或 Redis 连接故障导致。
     */
    boolean assembleActivitySku(Long sku);

    boolean assembleActivitySkuByActivityId(Long activityId);
}