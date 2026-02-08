package com.c.domain.credit.service;

import com.c.domain.credit.model.entity.TradeEntity;

/**
 * 积分调账服务接口
 * 职责：负责用户积分账户的额度调整（增加/扣减），并记录相应的交易流水。
 *
 * @author cyh
 * @date 2026/02/08
 */
public interface ICreditAdjustService {

    /**
     * 执行积分交易下单
     * 1. 包含：增加积分（正向）或 扣减积分（逆向）
     * 2. 特性：全链路幂等保证，基于 outBusinessNo 防重
     *
     * @param tradeEntity 交易实体对象（包含用户、金额、类型、外部单号）
     * @return 内部交易流水单号 (orderId)
     */
    String createOrder(TradeEntity tradeEntity);

}