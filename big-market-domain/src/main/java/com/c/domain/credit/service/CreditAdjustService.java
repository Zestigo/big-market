package com.c.domain.credit.service;

import com.c.domain.credit.model.aggregate.TradeAggregate;
import com.c.domain.credit.model.entity.TradeEntity;
import com.c.domain.credit.repository.ICreditRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * 积分调账领域服务实现
 * 职责：作为积分领域的核心编排者，负责调度聚合根完成业务逻辑转化，并驱动资源库进行持久化。
 *
 * @author cyh
 * @date 2026/02/08
 */
@Slf4j
@Service
public class CreditAdjustService implements ICreditAdjustService {

    @Resource
    private ICreditRepository creditRepository;

    /**
     * 创建积分交易订单（包含调账与流水记账）
     * * 业务流程：
     * 1. 指令转化：通过 {@link TradeEntity#toAggregate()} 将外部交易指令转化为内部领域聚合根。
     * 2. 原子持久化：调用 Repository 开启事务，同步执行【积分流水写入】与【账户余额变动】。
     * 3. 幂等控制：整体流程依赖 outBusinessNo 在数据库层的唯一索引实现全链路幂等。
     *
     * @param tradeEntity 交易实体指令对象
     * @return 内部生成的唯一交易流水单号 (orderId)
     */
    @Override
    public String createOrder(TradeEntity tradeEntity) {
        log.info("积分调账开始 userId:{} tradeName:{} outBusinessNo:{}",
                tradeEntity.getUserId(), tradeEntity.getTradeName(), tradeEntity.getOutBusinessNo());

        // 1. 转换：封装业务意图。Service 层不再感知具体的参数拼装，保持高度抽象。
        TradeAggregate tradeAggregate = tradeEntity.toAggregate();

        // 2. 执行：驱动资源库。将内存中的聚合状态“原子化”地同步至物理数据库。
        creditRepository.saveUserCreditTradeOrder(tradeAggregate);

        // 从聚合根中获取内部生成的流水单号返回给调用方
        String orderId = tradeAggregate.getCreditOrderEntity().getOrderId();
        log.info("积分调账完成 userId:{} orderId:{}", tradeEntity.getUserId(), orderId);

        return orderId;
    }
}