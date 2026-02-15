package com.c.domain.activity.service.quota.policy.impl;

import com.c.domain.activity.model.aggregate.CreateQuotaOrderAggregate;
import com.c.domain.activity.model.vo.OrderStateVO;
import com.c.domain.activity.repository.IActivityRepository;
import com.c.domain.activity.service.quota.policy.ITradePolicy;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 返利无支付交易策略
 * 处理不需要支付的业务场景，直接完成订单并更新账户额度。
 *
 * @author cyh
 * @date 2026/02/09
 */
@Component("rebate_no_pay_trade")
public class RebateNoPayTradePolicy implements ITradePolicy {

    /** 活动仓储对象 */
    private final IActivityRepository activityRepository;

    public RebateNoPayTradePolicy(IActivityRepository activityRepository) {
        this.activityRepository = activityRepository;
    }

    /**
     * 执行无支付交易
     * 变更订单状态为完成，清零支付金额，并持久化非支付类订单数据。
     *
     * @param createQuotaOrderAggregate 额度下单聚合根
     */
    @Override
    public void trade(CreateQuotaOrderAggregate createQuotaOrderAggregate) {
        createQuotaOrderAggregate.setOrderState(OrderStateVO.COMPLETED);
        createQuotaOrderAggregate
                .getActivityOrderEntity()
                .setPayAmount(BigDecimal.ZERO);
        activityRepository.doSaveNoPayOrder(createQuotaOrderAggregate);
    }
}