package com.c.infrastructure.dao;

import com.c.infrastructure.po.RuleTreeNode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 规则决策树节点查询 DAO
 * 负责访问 rule_tree_node 表，获取决策树中所有具体环节的配置信息。
 *
 * @author cyh
 * @since 2026/01/20
 */
@Mapper
public interface IRuleTreeNodeDao {

    /**
     * 根据决策树 ID 查询其关联的所有节点信息
     * 用于在内存中构建整棵规则树的拓扑结构
     *
     * @param treeId 决策树唯一标识
     * @return 规则树节点持久化对象列表
     */
    List<RuleTreeNode> queryRuleTreeNodeListByTreeId(String treeId);

    /**
     * 批量查询指定规则树中的锁定规则节点
     * 用于快速获取多个奖品对应的解锁次数阈值
     *
     * @param treeIds 规则树 ID 数组
     * @return 过滤后的规则锁定节点列表
     */
    List<RuleTreeNode> queryRuleLocks(String[] treeIds);
}