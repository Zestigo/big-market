package com.c.domain.activity.service;

import com.c.domain.activity.model.entity.PartakeRaffleActivityEntity;
import com.c.domain.activity.model.entity.UserRaffleOrderEntity;

/**
 * 抽奖活动参与领域服务接口
 * 1. 准入控制：校验用户参与资格（活动状态、有效期等）。
 * 2. 额度损耗：原子扣减用户多级账户（总/月/日）的参与次数。
 * 3. 幂等交付：通过检索“未消费订单”确保用户操作的幂等性与重入逻辑。
 *
 * @author cyh
 * @since 2026/02/01
 */
public interface IRaffleActivityPartakeService {

    /**
     * 创建抽奖单（简化调用模式）
     * 适用于常规参与场景，内部会自动封装为 PartakeRaffleActivityEntity 执行。
     *
     * @param userId     用户唯一标识
     * @param activityId 活动唯一标识
     * @return {@link UserRaffleOrderEntity} 用户抽奖单（包含策略 ID、订单凭证）
     */
    UserRaffleOrderEntity createOrder(String userId, Long activityId);

    /**
     * 创建抽奖单（标准参与模式）
     * 1. 幂等检查：检索是否存在“已创建未消费”的订单，若有则直接返回，防止额度超扣。
     * 2. 基础校验：验证活动是否在有效期内、状态是否为开启、SKU 库存是否充足。
     * 3. 额度核减：同步扣减用户账户的总次数、日次数及月次数。
     * 4. 聚合持久化：在事务中完成账户变更记录与参与订单（UserRaffleOrder）的落库。
     *
     * @param partakeRaffleActivityEntity 参与活动请求实体（包含用户 ID、活动 ID 及当前的日期标识）
     * @return {@link UserRaffleOrderEntity} 用户抽奖订单凭证
     * @throws com.c.types.exception.AppException 异常场景：次数耗尽、活动失效、重复参与等
     */
    UserRaffleOrderEntity createOrder(PartakeRaffleActivityEntity partakeRaffleActivityEntity);

}