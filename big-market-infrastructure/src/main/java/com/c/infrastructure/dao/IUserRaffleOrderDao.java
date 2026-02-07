package com.c.infrastructure.dao;

import com.c.infrastructure.po.UserRaffleOrder;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户抽奖订单数据访问接口
 * 职责：
 * 1. 凭证记录：持久化用户参与活动的合法凭证（订单）。
 * 2. 状态机流转：管理订单从 'CREATE' 到 'USED' 的原子转换。
 *
 * @author cyh
 * @date 2026/02/05
 */
@Mapper
public interface IUserRaffleOrderDao {

    /**
     * 查询待使用订单
     * 场景：用于参与活动链路的幂等重入。若用户已扣额度但未抽奖，直接返回该订单。
     *
     * @param userRaffleOrderReq 包含 userId, activityId
     * @return 待使用的订单快照
     */
    UserRaffleOrder queryNoUsedRaffleOrder(UserRaffleOrder userRaffleOrderReq);

    /**
     * 插入用户抽奖订单
     * 场景：在 saveCreatePartakeOrderAggregate 事务中执行。
     *
     * @param userRaffleOrder 订单对象
     */
    void insert(UserRaffleOrder userRaffleOrder);

    /**
     * 原子更新订单状态为【已使用】
     * 逻辑：update ... set state = 'USED' where userId = ? and orderId = ? and state = 'CREATE'
     * 场景：用户完成抽奖动作后，异步或同步更新订单状态。
     *
     * @return 1-更新成功；0-幂等拦截（订单已消耗或不存在）
     */
    int updateUserRaffleOrderStateUsed(UserRaffleOrder userRaffleOrder);

}