package com.c.domain.activity.service.quota.policy.impl;

import com.c.domain.activity.model.aggregate.CreateQuotaOrderAggregate;
import com.c.domain.activity.model.vo.OrderStateVO;
import com.c.domain.activity.repository.IActivityRepository;
import com.c.domain.activity.service.quota.policy.ITradePolicy;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

/**
 * 积分支付交易策略
 * 处理需要账户积分或余额支付的场景，将订单置为待支付状态并记录信用支付流水。
 *
 * @author cyh
 * @date 2026/02/09
 */
@Component("credit_pay_trade")
public class CreditPayTradePolicy implements ITradePolicy {

    /** 活动仓储对象 */
    private final IActivityRepository activityRepository;

    public CreditPayTradePolicy(IActivityRepository activityRepository) {
        this.activityRepository = activityRepository;
    }

    /**
     * 执行积分支付交易
     * 变更订单为待支付状态，持久化信用支付订单并等待后续支付结果回调。
     *
     * @param createQuotaOrderAggregate 额度下单聚合根
     */
    @Override
    public void trade(CreateQuotaOrderAggregate createQuotaOrderAggregate) {
        createQuotaOrderAggregate.setOrderState(OrderStateVO.WAIT_PAY);
        activityRepository.doSaveCreditPayOrder(createQuotaOrderAggregate);
    }

}