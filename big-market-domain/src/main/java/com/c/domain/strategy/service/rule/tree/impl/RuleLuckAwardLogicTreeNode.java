package com.c.domain.strategy.service.rule.tree.impl;

import com.c.domain.strategy.model.vo.RuleLogicCheckTypeVO;
import com.c.domain.strategy.service.rule.tree.ILogicTreeNode;
import com.c.domain.strategy.service.rule.tree.factory.DefaultTreeFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 规则幸运奖励逻辑节点
 * 职责：该节点通常作为决策链路的兜底或特定奖励路径。其核心逻辑是直接“接管”流程，
 * 返回配置好的幸运奖品数据，不再进行后续节点的决策。
 *
 * @author cyh
 * @date 2026/01/19
 */
@Slf4j
@Component("rule_luck_award")
public class RuleLuckAwardLogicTreeNode implements ILogicTreeNode {

    /**
     * 执行幸运奖发放逻辑判定
     *
     * @param userId     用户ID
     * @param strategyId 策略ID
     * @param awardId    当前奖品ID：由于是幸运奖节点，该 ID 可能会被兜底奖品或固定奖品替换
     * @return TreeActionEntity 决策实体。
     * 返回 TAKE_OVER：代表接管流程，决策树至此终止。
     * 包含 StrategyAwardData：携带最终发放的奖励配置。
     */
    @Override
    public DefaultTreeFactory.TreeActionEntity logic(String userId, Long strategyId, Integer awardId) {
        log.info("规则树-幸运奖节点开始执行: userId:{}, strategyId:{}, awardId:{}", userId, strategyId, awardId);

        // TODO: 实际业务逻辑实现。
        // 通常此处的 awardId 和 awardRuleValue 应该从数据库配置中通过 strategyId 获取。
        // 这里暂时硬编码为示例值（101 奖品，规则值为 1,100）。

        return DefaultTreeFactory.TreeActionEntity.builder()
                                                  .ruleLogicCheckType(RuleLogicCheckTypeVO.TAKE_OVER)
                                                  .strategyAwardData(DefaultTreeFactory.StrategyAwardData.builder()
                                                                                                         .awardId(101)          // 最终发放的幸运奖品ID
                                                                                                         .awardRuleValue("1,100") // 奖品扩展规则（如：保底范围、积分值等）
                                                                                                         .build())
                                                  .build();
    }

}