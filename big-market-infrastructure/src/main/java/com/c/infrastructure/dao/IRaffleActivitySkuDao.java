package com.c.infrastructure.dao;

import com.c.infrastructure.po.RaffleActivitySku;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 活动商品 SKU 数据库访问接口
 *
 * @author cyh
 * @date 2026/02/16
 */
@Mapper
public interface IRaffleActivitySkuDao {

    /**
     * 根据 SKU 编号查询详情
     *
     * @param sku 活动商品唯一标识
     * @return 活动商品持久化对象
     */
    RaffleActivitySku queryActivitySku(Long sku);

    /**
     * 数据库库存原子性扣减（减一）
     *
     * @param sku 活动商品唯一标识
     */
    void updateActivitySkuStock(Long sku);

    /**
     * 强制清空/校准数据库库存为 0
     *
     * @param sku 活动商品唯一标识
     */
    void clearActivitySkuStock(Long sku);

    /**
     * 更新指定额度的库存数量
     *
     * @param sku   活动商品唯一标识
     * @param count 目标库存数值
     */
    void updateActivitySkuStockCount(@Param("sku") Long sku, @Param("count") Integer count);

    /**
     * 根据活动 ID 查询关联的 SKU 列表
     *
     * @param activityId 活动唯一标识 ID
     * @return 活动商品持久化对象集合
     */
    List<RaffleActivitySku> queryActivitySkuListByActivityId(Long activityId);
}