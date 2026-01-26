package com.c.infrastructure.dao;

import com.c.infrastructure.po.RaffleActivityOrder;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 抽奖活动订单 DAO (ShardingSphere 分库分表版)
 * ShardingSphere 通过拦截 JDBC 层 SQL 语句，根据配置文件中的 shardingColumn (user_id) 自动实现路由。
 *
 * @author cyh
 * @date 2026/01/25
 */
@Mapper
public interface IRaffleActivityOrderDao {

    /**
     * 插入活动订单
     *
     * @param raffleActivityOrder 订单实体。ShardingSphere 会自动从实体类中提取 user_id 字段进行分库分表路由。
     */
    void insert(RaffleActivityOrder raffleActivityOrder);

    /**
     * 根据用户ID查询活动订单列表
     *
     * @param userId 用户ID。
     *               SQL 执行时，ShardingSphere 识别到 WHERE 条件中的 user_id，从而定位到具体的物理库表。
     * @return 订单列表。如果查询未包含分片键，ShardingSphere 可能会执行全路由扫描（应尽量避免）。
     */
    List<RaffleActivityOrder> queryRaffleActivityOrderByUserId(String userId);

}