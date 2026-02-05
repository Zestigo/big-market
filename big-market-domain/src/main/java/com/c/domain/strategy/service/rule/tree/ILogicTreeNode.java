package com.c.domain.strategy.service.rule.tree;

import com.c.domain.strategy.service.rule.tree.factory.DefaultTreeFactory;

import java.util.Date;

/**
 * 规则树逻辑节点接口 (Decision Tree Node Interface)
 * 职责：定义决策树中单个原子判定节点的标准执行契约。
 * 设计模式：策略模式 (Strategy Pattern)。每个实现类对应一种特定的业务规则（如：库存检查、次数锁定、黑名单过滤）。
 * 协作关系：由 {@code IDecisionTreeEngine} 统一调用，根据 {@code logic} 的返回结果判定路径流转方向。
 *
 * @author cyh
 * @since 2026/01/19
 */
public interface ILogicTreeNode {

    /**
     * 执行节点核心判定逻辑
     *
     * @param userId      用户唯一标识。用于节点内的频控、准入、身份标识校验。
     * @param strategyId  策略配置 ID。用于获取该策略下对应规则的具体业务阈值。
     * @param awardId     预抽中的奖品 ID。作为该节点判定的目标对象（如验证该奖品是否被锁定）。
     * @param ruleValue   节点配置值。从数据库 {@code rule_tree_node} 表中加载，如：次数锁的门槛值 "100"。
     * @param endDateTime 活动截止时间。提供时效边界数据，辅助节点进行有效期相关的规则判断。
     * @return {@link DefaultTreeFactory.TreeActionEntity} 决策动作实体。
     * 包含两个关键维度：
     * 1. RuleLogicCheckType: 决策指令。ALLOW(放行，继续走连线)、TAKE_OVER(接管，直接返回结果)。
     * 2. StrategyAwardVO: 决策产出。在接管状态下，通常携带兜底奖品或拦截后的奖励信息。
     */
    DefaultTreeFactory.TreeActionEntity logic(String userId, Long strategyId, Integer awardId, String ruleValue, Date endDateTime);

}