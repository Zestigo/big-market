package com.c.infrastructure.dao;

import com.c.infrastructure.po.RaffleActivityOrder;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 抽奖活动订单数据访问接口
 *
 * @author cyh
 * @date 2026/01/25
 */
@Mapper
public interface IRaffleActivityOrderDao {

    /**
     * 插入活动订单记录
     *
     * @param raffleActivityOrder 订单实体
     */
    void insert(RaffleActivityOrder raffleActivityOrder);

    /**
     * 查询用户关联的所有活动订单
     *
     * @param userId 用户 ID
     * @return 订单列表
     */
    List<RaffleActivityOrder> queryRaffleActivityOrderByUserId(String userId);

    /**
     * 更新订单状态为完成态
     * 配合状态机校验，用于订单支付或返利完成后的状态变更。
     *
     * @param raffleActivityOrderReq 订单查询与更新参数
     * @return 更新行数，1 表示成功，0 表示已被处理或订单不存在
     */
    int updateOrderCompleted(RaffleActivityOrder raffleActivityOrderReq);

    /**
     * 查询特定订单详情
     *
     * @param raffleActivityOrder 包含查询条件的订单实体
     * @return 订单详细信息
     */
    RaffleActivityOrder queryRaffleActivityOrder(RaffleActivityOrder raffleActivityOrder);

}