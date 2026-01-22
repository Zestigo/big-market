package com.c.domain.strategy.service.rule.tree;

import com.c.domain.strategy.service.rule.tree.factory.DefaultTreeFactory;

/**
 * 规则树逻辑节点接口
 * * 职责：定义决策树中单个节点的标准执行行为。
 * 设计模式：该接口是策略模式的典型应用，每个具体的规则实现（如库存、锁、黑名单）
 * 均作为决策树中的一个原子判定点，由引擎统一驱动。
 *
 * @author cyh
 * @date 2026/01/19
 */
public interface ILogicTreeNode {

    /**
     * 执行节点核心业务逻辑
     *
     * @param userId     用户唯一标识，用于查询用户画像、行为记录等特征数据
     * @param strategyId 策略配置ID，用于获取当前业务场景下的规则阈值或配置参数
     * @param awardId    预抽中的奖品ID，作为该节点判定的目标对象（如判定该奖品的锁状态或库存）
     * @return {@link DefaultTreeFactory.TreeActionEntity} 逻辑执行后的决策实体。
     * 包含：
     * 1. ruleLogicCheckType: 决策流转指令（ALLOW-放行至下一节点，TAKE_OVER-接管/终止链路）
     * 2. strategyAwardData: 节点产出的奖励信息（通常在 TAKE_OVER 状态下携带最终奖品）
     */
    public DefaultTreeFactory.TreeActionEntity logic(String userId, Long strategyId, Integer awardId, String ruleValue);

}