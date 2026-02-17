package com.c.domain.activity.service;

import com.c.domain.activity.model.entity.ActivityAccountEntity;
import com.c.domain.activity.model.entity.DeliveryOrderEntity;
import com.c.domain.activity.model.entity.SkuRechargeEntity;
import com.c.domain.activity.model.entity.UnpaidActivityOrderEntity;

/**
 * 抽奖活动账户额度服务接口
 * 负责活动参与次数的充值下单、订单状态更新及账户额度查询。
 *
 * @author cyh
 * @date 2026/01/27
 */
public interface IRaffleActivityAccountQuotaService {

    /**
     * 创建抽奖活动订单
     * 执行用户资格校验、库存锁定、订单创建及账户额度更新。
     *
     * @param skuRechargeEntity 抽奖充值实体
     * @return 订单 ID
     */
    UnpaidActivityOrderEntity createOrder(SkuRechargeEntity skuRechargeEntity);

    /**
     * 更新订单状态
     * 处理投递单流水，完成订单状态变更或账户额度加减。
     *
     * @param deliveryOrderEntity 投递单实体
     */
    void updateOrder(DeliveryOrderEntity deliveryOrderEntity);

    /**
     * 查询用户当日累计参与次数
     *
     * @param activityId 活动 ID
     * @param userId     用户 ID
     * @return 当日已参与累计计数值
     */
    Integer queryRaffleActivityAccountDayPartakeCount(Long activityId, String userId);

    /**
     * 查询用户活动账户额度
     *
     * @param activityId 活动 ID
     * @param userId     用户 ID
     * @return 活动账户额度实体
     */
    ActivityAccountEntity queryActivityAccountEntity(Long activityId, String userId);

    /**
     * 查询用户活动已参与总次数
     *
     * @param activityId 活动 ID
     * @param userId     用户 ID
     * @return 已参与次数
     */
    Integer queryRaffleActivityAccountPartakeCount(Long activityId, String userId);
}