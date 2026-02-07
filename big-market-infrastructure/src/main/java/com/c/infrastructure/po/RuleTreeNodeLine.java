package com.c.infrastructure.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 持久化对象：规则树节点连线 (rule_tree_node_line)
 * <p>
 * 职责：描述决策树中节点与节点之间的指向关系，并承载判定流转的触发条件。
 * 作用：规则引擎根据当前节点的执行结果，比对连线上的限定条件，决定下一跳的目标节点。
 *
 * @author cyh
 * @date 2026/01/20
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RuleTreeNodeLine {

    /** 自增 ID：数据库物理主键 */
    private Long id;

    /** 规则树 ID：归属于哪棵决策树 */
    private String treeId;

    /** 起始节点 ID：规则判定的入口节点（From） */
    private String ruleNodeFrom;

    /** 目标节点 ID：判定通过后指向的下一个节点（To） */
    private String ruleNodeTo;

    /**
     * 限定规则类型：
     * 1: [=] 等于
     * 2: [>] 大于
     * 3: [<] 小于
     * 4: [>=] 大于等于
     * 5: [<=] 小于等于
     * 6: [enum] 枚举范围匹配
     */
    private String ruleLimitType;

    /** 限定规则比对值：用于与节点执行结果进行比对的具体数值或枚举区间 */
    private String ruleLimitValue;

    /** 创建时间 */
    private Date createTime;

    /** 更新时间 */
    private Date updateTime;

}