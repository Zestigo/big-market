package com.c.infrastructure.dao;

import com.c.infrastructure.po.RaffleActivitySKU;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 活动商品 SKU 数据库访问接口
 * 职责：
 * 1. 负责 raffle_activity_sku 表的基础数据检索与库存状态持久化。
 * 2. 作为库存更新的“最终物理层”，确保在高并发扣减逻辑后，DB 状态与缓存预扣状态对齐。
 * 3. 支撑活动预热（Armory）阶段的初始配置加载。
 *
 * @author cyh
 * @date 2026/01/28
 */
@Mapper
public interface IRaffleActivitySkuDao {

    /**
     * 根据 SKU 编号查询详情
     * 业务场景：在活动策略装配或库存预热时，获取关联的 activityId、stockCountSurplus 等关键属性。
     *
     * @param sku 活动商品唯一标识
     * @return {@link RaffleActivitySKU} 包含基础配置及当前剩余库存的持久化对象
     */
    RaffleActivitySKU queryActivitySku(Long sku);

    /**
     * 数据库库存原子性减一
     * 业务逻辑：配合 Redis 异步更新链路。SQL 实现必须包含 stock_count_surplus > 0 的前置检查。
     * 注意：在高并发场景下，此操作通常由消息队列异步触发，通过行锁保障数据一致性。
     *
     * @param sku 活动商品唯一标识
     */
    void updateActivitySkuStock(Long sku);

    /**
     * 强制清空/校准数据库库存
     * 业务逻辑：接收到缓存层发送的售罄（Zero）信号时，执行 DB 库存强刷，确保持久层不再有可用余量。
     *
     * @param sku 活动商品唯一标识
     */
    void clearActivitySkuStock(Long sku);

    /**
     * 指定额度的库存更新/重置
     * 业务场景：活动重新上架、库存手动补货或定时批量同步库存。
     * 注意：多参数传递时，必须显式指定 @Param 映射以匹配 XML 占位符。
     *
     * @param sku   活动商品唯一标识
     * @param count 待更新的目标库存数值或库存增量
     */
    void updateActivitySkuStockCount(@Param("sku") Long sku, @Param("count") Integer count);
}