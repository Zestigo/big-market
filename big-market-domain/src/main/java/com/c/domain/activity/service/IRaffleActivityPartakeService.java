package com.c.domain.activity.service;

import com.c.domain.activity.model.entity.PartakeRaffleActivityEntity;
import com.c.domain.activity.model.entity.UserRaffleOrderEntity;

/**
 * 抽奖活动参与服务接口
 * 1. 准入控制：校验用户是否有资格参与指定活动（有效期、状态等）。
 * 2. 额度管理：负责扣减用户的活动参与次数（总额度、月额度、日额度）。
 * 3. 幂等交付：确保用户在同一活动周期内，不会因为重复请求而导致额度超扣，并维护“未消费订单”的重入逻辑。
 *
 * @author cyh
 * @date 2026/02/01
 */
public interface IRaffleActivityPartakeService {

    /**
     * 创建抽奖单（参与活动核心动作）
     * * 业务逻辑编排：
     * 1. 幂等检索：优先查询是否存在【已创建但未使用的抽奖单】。若存在，则直接返回该单据，避免重复扣额度。
     * 2. 账户校验：检查用户在当前活动下的“总、月、日”账户余额是否充足。
     * 3. 额度扣减：采用原子操作扣减各维度账户次数。
     * 4. 单据持久化：同步生成用户抽奖订单（UserRaffleOrder），作为后续进入抽奖引擎的唯一凭证。
     *
     * @param partakeRaffleActivityEntity 参与抽奖活动请求实体（包含 userId, activityId 等关键参数）
     * @return UserRaffleOrderEntity 用户抽奖订单实体（包含 strategyId, orderId 等抽奖必备信息）
     * @throws com.c.types.exception.AppException 抛出异常场景：活动未开启、额度不足、非法参与等
     */
    UserRaffleOrderEntity createOrder(PartakeRaffleActivityEntity partakeRaffleActivityEntity);

}