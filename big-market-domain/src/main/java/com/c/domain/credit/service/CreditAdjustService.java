package com.c.domain.credit.service;

import com.c.domain.credit.event.CreditAdjustSuccessMessageEvent;
import com.c.domain.credit.model.aggregate.TradeAggregate;
import com.c.domain.credit.model.entity.TradeEntity;
import com.c.domain.credit.repository.ICreditRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * 积分调账领域服务实现
 *
 * @author cyh
 * @date 2026/02/09
 */
@Slf4j
@Service
public class CreditAdjustService implements ICreditAdjustService {

    @Resource
    private ICreditRepository creditRepository;
    @Resource
    private CreditAdjustSuccessMessageEvent messageEvent;

    @Override
    public String createOrder(TradeEntity tradeEntity) {
        log.info("积分调账开始 userId: {} outBusinessNo: {}", tradeEntity.getUserId(), tradeEntity.getOutBusinessNo());

        // 1. 转换指令：通过静态工厂构建聚合根初始态（生成 orderId）
        TradeAggregate tradeAggregate = tradeEntity.toAggregate();

        // 2. 自主装配：聚合根基于自身状态完成任务零件 (TaskEntity) 的构建
        tradeAggregate.createMessageTask(messageEvent);

        // 3. 存储聚合根：包含账户更新、流水记录与任务消息的事务持久化
        creditRepository.saveUserCreditTradeOrder(tradeAggregate);

        log.info("积分调账完成 userId: {} orderId: {}", tradeEntity.getUserId(), tradeAggregate
                .getCreditOrderEntity()
                .getOrderId());

        return tradeAggregate
                .getCreditOrderEntity()
                .getOrderId();
    }
}