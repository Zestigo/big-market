package com.c.domain.strategy.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 规则树节点关系连线 VO
 * 定义了决策树中节点与节点之间的流转关系及触发条件。
 *
 * @author cyh
 * @date 2026/01/19
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RuleTreeNodeLineVO {

    /** 规则树ID：所属决策树的唯一标识 */
    private String treeId;

    /** 起始节点：规则执行的源头节点 Key（如：user_level） */
    private String ruleNodeFrom;

    /** 目标节点：满足过滤条件后，指向的下一个节点 Key */
    private String ruleNodeTo;

    /** 判定操作符：定义如何比对当前节点的输出值（如：EQUAL, GT, ENUM） */
    private RuleLimitTypeVO ruleLimitType;

    /** 判定预期值：与当前节点输出值进行比对的基准目标值（如：等级 "VIP"） */
    private RuleLogicCheckTypeVO ruleLimitValue;

}