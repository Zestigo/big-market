package com.c.domain.strategy.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 规则树根对象 VO
 * 该类是决策树配置的顶层容器，封装了树的基本信息、执行入口以及全量节点数据。
 *
 * @author cyh
 * @date 2026/01/19
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RuleTreeVO {

    /** 规则树ID：数据库唯一标识，用于在业务逻辑中指定调用哪棵规则树 */
    private Integer treeId;

    /** 规则树名称：直观展示业务含义（如：抽奖概率规则树、风控准入规则树） */
    private String treeName;

    /** 规则树描述：详细记录该树的设计意图及业务规则背景 */
    private String treeDesc;

    /** 规则根节点：决策引擎执行的起点。其值对应 treeNodeMap 中的一个 ruleKey */
    private String treeRootRuleNode;

    /**
     * 规则节点映射表：
     * Key 为节点标识（ruleKey），Value 为对应的节点配置对象。
     * 引擎通过该 Map 快速检索每一个逻辑判定点的详细内容。
     */
    private Map<String, RuleTreeNodeVO> treeNodeMap;

}