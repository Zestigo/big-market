package com.c.infrastructure.dao;

import com.c.infrastructure.po.UserBehaviorRebateOrder;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 用户行为返利订单流水 DAO
 *
 * @author cyh
 * @date 2026/02/05
 */
@Mapper
public interface IUserBehaviorRebateOrderDao {

    /**
     * 新增用户行为返利订单记录
     *
     * @param userBehaviorRebateOrder 用户行为返利订单持久化对象
     */
    void insert(UserBehaviorRebateOrder userBehaviorRebateOrder);

    /**
     * 根据外部业务单号查询订单
     *
     * @param userBehaviorRebateOrderReq 查询请求对象（包含 userId 和 outBusinessNo）
     * @return 返回对应外部单号的流水记录列表
     */
    List<UserBehaviorRebateOrder> queryOrderByOutBusinessNo(UserBehaviorRebateOrder userBehaviorRebateOrderReq);

}