package com.c.domain.credit.service;

import com.c.domain.credit.model.entity.CreditAccountEntity;
import com.c.domain.credit.model.entity.TradeEntity;

/**
 * 积分调账服务接口
 *
 * @author cyh
 * @date 2026/02/16
 */
public interface ICreditAdjustService {

    /**
     * 创建积分交易订单
     * 执行逻辑：支持正向积分增加与逆向积分扣减，通过外部业务单号保证幂等。
     *
     * @param tradeEntity 交易实体对象
     * @return 内部交易流水单号 (orderId)
     */
    String createOrder(TradeEntity tradeEntity);

    /**
     * 查询用户积分账户信息
     *
     * @param userId 用户唯一标识 ID
     * @return 积分账户实体（包含当前可用余额）
     */
    CreditAccountEntity queryUserCreditAccount(String userId);

}