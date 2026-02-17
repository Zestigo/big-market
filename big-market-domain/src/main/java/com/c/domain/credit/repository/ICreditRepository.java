package com.c.domain.credit.repository;

import com.c.domain.credit.model.aggregate.TradeAggregate;
import com.c.domain.credit.model.entity.CreditAccountEntity;

/**
 * 积分仓储服务接口
 *
 * @author cyh
 * @date 2026/02/16
 */
public interface ICreditRepository {

    /**
     * 保存积分交易订单并同步更新账户额度
     *
     * @param tradeAggregate 积分交易聚合根（包含流水实体与账户实体）
     */
    void saveUserCreditTradeOrder(TradeAggregate tradeAggregate);

    /**
     * 根据用户 ID 查询积分账户信息
     *
     * @param userId 用户唯一标识 ID
     * @return 积分账户实体对象
     */
    CreditAccountEntity queryUserCreditAccount(String userId);

}