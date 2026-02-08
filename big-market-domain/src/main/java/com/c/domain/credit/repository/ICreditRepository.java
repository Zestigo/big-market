package com.c.domain.credit.repository;

import com.c.domain.credit.model.aggregate.TradeAggregate;

/**
 * 积分仓储服务接口
 * 职责：负责积分交易聚合根的持久化操作，对接底层数据库（MySQL/Redis等）。
 *
 * @author cyh
 * @date 2026/02/08
 */
public interface ICreditRepository {

    /**
     * 保存积分交易订单及更新账户额度
     * 核心实现要求：
     * 1. 事务性：必须保证【积分流水记录】与【账户余额更新】在同一个数据库事务内完成。
     * 2. 幂等性：利用 {@link TradeAggregate} 中的 outBusinessNo 作为唯一约束，防止重复记账。
     * 3. 策略路由：根据聚合根内 TradeType 的方向（正向/逆向），决定执行加法 Upsert 或 满足余额校验的原子扣减。
     *
     * @param tradeAggregate 积分交易聚合根
     */
    void saveUserCreditTradeOrder(TradeAggregate tradeAggregate);

}