package com.c.domain.credit.service;

import com.c.domain.credit.event.CreditAdjustSuccessMessageEvent;
import com.c.domain.credit.model.aggregate.TradeAggregate;
import com.c.domain.credit.model.entity.CreditAccountEntity;
import com.c.domain.credit.model.entity.TradeEntity;
import com.c.domain.credit.repository.ICreditRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * 积分调账领域服务实现
 *
 * @author cyh
 * @date 2026/02/16
 */
@Slf4j
@Service
public class CreditAdjustService implements ICreditAdjustService {

    @Resource
    private ICreditRepository creditRepository;
    @Resource
    private CreditAdjustSuccessMessageEvent messageEvent;

    /**
     * 创建积分交易订单
     *
     * @param tradeEntity 交易请求实体
     * @return 内部生成的交易流水单号
     */
    @Override
    public String createOrder(TradeEntity tradeEntity) {
        log.info("积分调账开始 userId:{} outBusinessNo:{}", tradeEntity.getUserId(), tradeEntity.getOutBusinessNo());

        // 1. 构建积分交易聚合根
        TradeAggregate tradeAggregate = tradeEntity.toAggregate();

        // 2. 创建消息发送任务（用于后续发送 MQ 消息）
        tradeAggregate.createMessageTask(messageEvent);

        // 3. 持久化聚合根：在同一个事务内完成账户更新、流水记录与任务落地
        creditRepository.saveUserCreditTradeOrder(tradeAggregate);

        String orderId = tradeAggregate
                .getCreditOrderEntity()
                .getOrderId();
        log.info("积分调账完成 userId:{} orderId:{}", tradeEntity.getUserId(), orderId);

        return orderId;
    }

    /**
     * 查询用户积分账户信息
     *
     * @param userId 用户唯一标识 ID
     * @return 用户积分账户实体
     */
    @Override
    public CreditAccountEntity queryUserCreditAccount(String userId) {
        // 直接通过仓储层检索账户实时余额
        return creditRepository.queryUserCreditAccount(userId);
    }
}