package com.c.domain.strategy.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Collections;
import java.util.Map;

/**
 * 规则决策树根对象
 * <p>
 * 职责：作为决策树的顶层上下文，管理整棵树的生命周期配置。
 * 作用：定义规则执行的起点（Root Node），并持有整棵树所有决策节点的索引图谱。
 *
 * @author cyh
 * @date 2026/01/19
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RuleTreeVO implements Serializable {

    private static final long serialVersionUID = 5384162453443198115L;

    /** 规则树 ID：唯一标识一个业务决策链路（如：抽奖通用规则树） */
    private String treeId;

    /** 规则树名称：便于后台管理与业务辨识 */
    private String treeName;

    /** 规则树描述：记录该树的逻辑意图及设计说明 */
    private String treeDesc;

    /** 决策入口节点：定义整棵树从哪个 ruleKey 开始执行判断 */
    private String treeRootRuleNode;

    /** 全量决策节点索引：以 ruleKey 为键，快速定位具体的节点逻辑 */
    private Map<String, RuleTreeNodeVO> treeNodeMap;

    /**
     * 安全获取节点索引图
     * 防止引擎解析时因空配置导致的 NPE 风险。
     */
    public Map<String, RuleTreeNodeVO> getTreeNodeMap() {
        return treeNodeMap == null ? Collections.emptyMap() : treeNodeMap;
    }

}