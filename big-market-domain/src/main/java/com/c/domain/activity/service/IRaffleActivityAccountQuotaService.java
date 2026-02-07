package com.c.domain.activity.service;

import com.c.domain.activity.model.entity.ActivityAccountEntity;
import com.c.domain.activity.model.entity.SkuRechargeEntity;

/**
 * 抽奖活动账户额度服务接口
 * 负责处理活动参与次数的充值（下单）、账户额度查询等业务逻辑。
 *
 * @author cyh
 * @since 2026/01/27
 */
public interface IRaffleActivityAccountQuotaService {

    /**
     * 创建抽奖活动订单（SKU 充值模式）
     * 业务逻辑：校验用户充值资格 -> 锁定库存 -> 创建活动参与订单 -> 更新账户总额度。
     *
     * @param skuRechargeEntity 抽奖充值实体，包含：用户ID、SKU、业务流水号等
     * @return 订单 ID（唯一幂等标识）
     */
    String createOrder(SkuRechargeEntity skuRechargeEntity);

    /**
     * 查询用户当日累计已参与抽奖活动的次数
     * 业务用途：常用于前端展示活动进度条，或后端用于“日参与次数锁（RuleLock）”的阈值校验。
     *
     * @param activityId 活动 ID（明确查询哪个活动的参与记录）
     * @param userId     用户 ID
     * @return 当日该用户已参与活动的累计计数值
     */
    Integer queryRaffleActivityAccountDayPartakeCount(Long activityId, String userId);

    /**
     * 查询用户活动账户额度实体
     * 根据活动ID和用户ID，获取用户在该活动下的总额度、日额度、月额度及其剩余消耗情况。
     *
     * @param activityId 活动ID
     * @param userId     用户唯一ID
     * @return ActivityAccountEntity 活动账户额度实体对象
     */
    ActivityAccountEntity queryActivityAccountEntity(Long activityId, String userId);

    /**
     * 查询用户在特定活动下的已参与抽奖次数
     *
     * @param activityId 活动ID
     * @param userId     用户ID
     * @return 已参与次数
     */
    Integer queryRaffleActivityAccountPartakeCount(Long activityId, String userId);
}