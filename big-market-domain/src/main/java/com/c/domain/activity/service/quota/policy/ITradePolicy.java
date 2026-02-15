package com.c.domain.activity.service.quota.policy;

import com.c.domain.activity.model.aggregate.CreateQuotaOrderAggregate;

/**
 * 账户额度交易策略接口
 * 定义额度交易的核心执行逻辑。该接口是策略模式的抽象，用于处理不同业务场景下的
 * 额度扣减、增加或状态同步。
 *
 * @author cyh
 * @date 2026/02/09
 */
public interface ITradePolicy {

    /**
     * 执行额度交易
     *
     * @param createQuotaOrderAggregate 额度下单聚合根，承载了账户信息、交易流水及业务校验逻辑
     * @throws RuntimeException 交易失败时抛出，例如余额不足、风控拦截或系统超时
     */
    void trade(CreateQuotaOrderAggregate createQuotaOrderAggregate);

}