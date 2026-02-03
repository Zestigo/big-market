package com.c.infrastructure.dao;

import com.c.infrastructure.po.UserRaffleOrder;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户抽奖订单数据访问接口
 * 1. 记录持久化：负责将用户参与活动后生成的抽奖单据存入物理表。
 * 2. 幂等支撑：提供基于用户和活动维度的订单状态检索，防止额度重复扣减。
 *
 * @author cyh
 * @date 2026/02/01
 */
@Mapper
public interface IUserRaffleOrderDao {

    /**
     * 查询用户是否存在【已创建但未使用】的抽奖订单
     * 用于参与活动链路中的幂等性校验。若用户此前已成功扣减额度并生成订单，
     * 但因网络波动未实际进入抽奖，此方法将返回该订单，实现流程的可重入性。
     *
     * @param userRaffleOrderReq 包含 userId, activityId 的查询请求对象
     * @return 匹配到的订单记录（通常包含 orderId, strategyId 等关键信息）
     */
    UserRaffleOrder queryNoUsedRaffleOrder(UserRaffleOrder userRaffleOrderReq);

    /**
     * 插入新的用户抽奖订单
     * 1. 事务约束：该方法通常在 `saveCreatePartakeOrderAggregate` 事务块中执行，
     * 与账户额度的扣减保持强一致性。
     * 2. 唯一索引：数据库层应对 orderId 或业务组合键设置唯一约束，防止并发重复写入。
     *
     * @param userRaffleOrder 抽奖订单持久化对象
     */
    void insert(UserRaffleOrder userRaffleOrder);

    int updateUserRaffleOrderStateUsed(UserRaffleOrder userRaffleOrder);
}