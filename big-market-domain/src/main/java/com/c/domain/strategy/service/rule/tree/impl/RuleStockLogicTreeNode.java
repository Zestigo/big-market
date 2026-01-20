package com.c.domain.strategy.service.rule.tree.impl;

import com.c.domain.strategy.model.vo.RuleLogicCheckTypeVO;
import com.c.domain.strategy.service.rule.tree.ILogicTreeNode;
import com.c.domain.strategy.service.rule.tree.factory.DefaultTreeFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 规则库存逻辑节点
 * 职责：在决策链条中负责奖品的实时库存准入校验。
 * 作用：确保只有在库存扣减成功或库存充足的情况下，用户才能继续执行后续的发奖流程，是风险控制的核心节点。
 *
 * @author cyh
 * @date 2026/01/19
 */
@Slf4j
@Component("rule_stock")
public class RuleStockLogicTreeNode implements ILogicTreeNode {

    /**
     * 执行库存校验与扣减判定
     *
     * @param userId     用户唯一ID
     * @param strategyId 策略配置ID
     * @param awardId    待校验库存的奖品ID
     * @return TreeActionEntity 决策实体。
     * 返回 ALLOW：库存充足且预扣成功，允许进入下一个决策节点（如：锁定校验、中奖通知）。
     * 返回 TAKE_OVER：库存不足，流程被接管，通常会流转至“幸运奖/兜底奖”节点。
     */
    @Override
    public DefaultTreeFactory.TreeActionEntity logic(String userId, Long strategyId, Integer awardId) {
        log.info("规则树-库存校验节点开始执行: userId:{}, strategyId:{}, awardId:{}", userId, strategyId, awardId);

        // TODO: 实际业务逻辑实现
        // 1. 调用库存中心/Redis 预扣减当前 awardId 的库存
        // 2. 如果扣减成功，返回 RuleLogicCheckTypeVO.ALLOW
        // 3. 如果库存不足（扣减失败），返回 RuleLogicCheckTypeVO.TAKE_OVER

        // 目前占位逻辑：暂存为接管（TAKE_OVER），实际接入时请根据库存状态动态判定
        return DefaultTreeFactory.TreeActionEntity.builder()
                                                  .ruleLogicCheckType(RuleLogicCheckTypeVO.TAKE_OVER)
                                                  .build();
    }

}