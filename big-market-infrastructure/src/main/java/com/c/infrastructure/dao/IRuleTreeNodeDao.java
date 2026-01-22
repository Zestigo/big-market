package com.c.infrastructure.dao;

import com.c.infrastructure.po.RuleTreeNode;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 规则决策树节点查询 DAO
 * * 职责：
 * 负责访问 `rule_tree_node` 表，获取决策树中所有具体环节的配置信息。
 * 节点包含了具体的业务逻辑 Key（如库存校验、次数限制）以及对应的配置值。
 *
 *
 *
 * @author cyh
 * @date 2026/01/20
 */
@Mapper
public interface IRuleTreeNodeDao {

    /**
     * 根据决策树ID查询其关联的所有节点信息
     * * 业务用途：
     * 在加载整棵规则树时，一次性查出该树下的所有节点，用于在内存中构建节点 Map。
     * 每个节点包含：rule_key（逻辑标识）、rule_desc（描述）、rule_value（配置参数）。
     *
     * @param treeId 决策树唯一标识
     * @return 规则树节点持久化对象列表
     */
    List<RuleTreeNode> queryRuleTreeNodeListByTreeId(String treeId);

}