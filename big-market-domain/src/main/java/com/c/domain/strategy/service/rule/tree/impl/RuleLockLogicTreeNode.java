package com.c.domain.strategy.service.rule.tree.impl;

import com.c.domain.strategy.model.vo.RuleLogicCheckTypeVO;
import com.c.domain.strategy.service.rule.tree.ILogicTreeNode;
import com.c.domain.strategy.service.rule.tree.factory.DefaultTreeFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 规则锁逻辑节点
 * * 职责：负责校验当前抽奖行为是否满足预设的规则锁条件。
 * 典型场景：判断用户累计抽奖次数是否达到指定阈值，从而决定是否解锁并准许获取该奖品。
 *
 * @author cyh
 * @date 2026/01/19
 */
@Slf4j
@Component("rule_lock")
public class RuleLockLogicTreeNode implements ILogicTreeNode {

    /**
     * 执行规则锁判定逻辑
     * * @param userId     用户唯一ID
     * @param strategyId 策略配置ID
     * @param awardId    当前进行锁校验的目标奖品ID
     * @return TreeActionEntity 决策结果。返回 ALLOW 则代表解锁成功，继续后续逻辑；返回 TAKE_OVER 则代表锁定，流程被接管/拦截。
     */
    @Override
    public DefaultTreeFactory.TreeActionEntity logic(String userId, Long strategyId, Integer awardId) {
        log.info("规则树-规则锁节点开始校验: userId:{}, strategyId:{}, awardId:{}", userId, strategyId, awardId);

        // TODO: 实际业务逻辑实现。
        // 1. 获取该策略配置的解锁阈值（如：需抽奖 n 次）
        // 2. 查询用户当前已抽奖次数
        // 3. 比较次数，判定是放行(ALLOW)还是拦截(TAKE_OVER)

        // 目前默认返回放行逻辑，待接入 Repository 后完善
        return DefaultTreeFactory.TreeActionEntity.builder()
                                                  .ruleLogicCheckType(RuleLogicCheckTypeVO.ALLOW)
                                                  .build();
    }

}