package com.c.infrastructure.dao;

import com.c.infrastructure.po.RaffleActivityOrder;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 抽奖活动订单数据访问接口
 *
 * @author cyh
 * @date 2026/02/16
 */
@Mapper
public interface IRaffleActivityOrderDao {

    /**
     * 插入活动订单记录
     *
     * @param raffleActivityOrder 订单实体信息
     */
    void insert(RaffleActivityOrder raffleActivityOrder);

    /**
     * 根据用户 ID 查询所有活动订单列表
     *
     * @param userId 用户唯一标识 ID
     * @return 该用户下的所有订单集合
     */
    List<RaffleActivityOrder> queryRaffleActivityOrderByUserId(String userId);

    /**
     * 更新订单状态为完成态
     * 执行逻辑：基于 userId 和 outBusinessNo 锁定待支付订单，变更为已完成状态
     *
     * @param raffleActivityOrderReq 包含用户ID和外部单号的请求对象
     * @return 更新影响的行数
     */
    int updateOrderCompleted(RaffleActivityOrder raffleActivityOrderReq);

    /**
     * 查询特定订单详情 (根据用户ID和外部单号)
     *
     * @param raffleActivityOrder 包含查询条件的订单实体
     * @return 匹配的订单详情记录
     */
    RaffleActivityOrder queryRaffleActivityOrder(RaffleActivityOrder raffleActivityOrder);

    /**
     * 查询用户针对特定 SKU 的待支付订单
     * 常用于下单前的防重校验或继续支付逻辑
     *
     * @param raffleActivityOrder 包含用户ID、SKU、待支付状态的请求对象
     * @return 待支付的订单信息
     */
    RaffleActivityOrder queryUnpaidActivityOrder(RaffleActivityOrder raffleActivityOrder);
}