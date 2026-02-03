package com.c.infrastructure.dao;

import com.c.infrastructure.po.RuleTree;
import org.apache.ibatis.annotations.Mapper;

/**
 * 规则决策树配置查询 DAO
 * 职责：负责访问 `rule_tree` 表，获取决策树的根节点信息、树名称及基本描述。
 *
 * @author cyh
 * @date 2026/01/20
 */
@Mapper
public interface IRuleTreeDao {

    /**
     * 根据决策树ID查询树根及基础配置信息
     *
     * @param treeId 决策树唯一标识（例如：tree_lock_stock）
     * @return 规则树持久化对象（包含 tree_id, tree_name, tree_root_rule_key 等）
     */
    RuleTree queryRuleTreeByTreeId(String treeId);

}