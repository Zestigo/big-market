package com.c.infrastructure.dao;

import com.c.infrastructure.po.RuleTreeNodeLine;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 规则决策树节点连线查询 DAO
 * * 职责：
 * 负责访问 `rule_tree_node_line` 表，获取决策树中节点之间的连接关系。
 * 连线定义了决策流从“起始节点”流向“目标节点”的过滤条件和判定规则。
 *
 * @author cyh
 * @date 2026/01/20
 */
@Mapper
public interface IRuleTreeNodeLineDao {

    /**
     * 根据决策树ID查询其关联的所有连线信息
     * * 业务用途：
     * 用于在内存中构建决策树的拓扑结构。
     * 每条连线包含关键信息：
     * 1. rule_node_from: 入口节点（从哪来）
     * 2. rule_node_to: 出口节点（到哪去）
     * 3. rule_limit_type: 规则限定类型（如：EQUAL, GT, LT, GE, LE, ENUM）
     * 4. rule_limit_value: 规则限定值（如：ALLOW, TAKE_OVER）
     *
     * @param treeId 决策树唯一标识
     * @return 规则树节点连线持久化对象列表
     */
    List<RuleTreeNodeLine> queryRuleTreeNodeLineListByTreeId(String treeId);

}