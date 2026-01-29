package com.c.infrastructure.dao;

import com.c.infrastructure.po.RaffleActivitySKU;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author cyh
 * @description 商品SKU库存仓储数据库访问层
 * 职责：负责 raffle_activity_sku 表的增删改查。
 * 在高并发抽奖中，该层主要配合 Redis 预扣减，进行数据库库存的最终一致性更新。
 * @date 2026/01/28
 */
@Mapper
public interface IRaffleActivitySkuDao {

    /**
     * 查询活动 SKU 详情
     * 用于活动装配（预热）时从 DB 获取初始配置和库存信息。
     *
     * @param sku 商品 SKU 编号
     * @return 包含活动 ID、库存、次数配置等信息的持久化对象
     */
    RaffleActivitySKU queryActivitySku(Long sku);

    /**
     * 数据库库存更新（扣减 1）
     * 场景：通常由 MQ 消费者调用，将 Redis 预扣减的结果同步到数据库。
     * SQL 建议：update raffle_activity_sku set stock_count = stock_count - 1 where sku = #{sku} and
     * stock_count > 0
     *
     * @param sku 商品 SKU 编号
     */
    void updateActivitySkuStock(Long sku);

    /**
     * 清空数据库库存（逻辑或物理清零）
     * 场景：当 Redis 检测到库存售罄并发送清零事件时，强制同步 DB 库存为 0，防止数据不一致。
     *
     * @param sku 商品 SKU 编号
     */
    void clearActivitySkuStock(Long sku);

}