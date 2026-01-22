package com.c.domain.strategy.service.rule.tree.factory;

import com.c.domain.strategy.model.vo.RuleLogicCheckTypeVO;
import com.c.domain.strategy.model.vo.RuleTreeVO;
import com.c.domain.strategy.service.rule.tree.ILogicTreeNode;
import com.c.domain.strategy.service.rule.tree.factory.engine.IDecisionTreeEngine;
import com.c.domain.strategy.service.rule.tree.factory.engine.impl.DecisionTreeEngine;
import lombok.*;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 规则树工厂类
 * * 核心职责：作为决策树引擎的创建中心，负责将 Spring 容器注入的逻辑节点组（Strategy）
 * 与具体的规则树配置（VO）进行解耦装配，生成可执行的引擎实例。
 *
 * @author cyh
 * @date 2026/01/19
 */
@Service
public class DefaultTreeFactory {

    /**
     * Spring自动注入所有ILogicTreeNode实现类
     * 设计意图：利用Spring的特性，自动收集所有规则节点，无需手动注册
     */
    private final Map<String, ILogicTreeNode> logicTreeNodeGroup;

    /**
     * 构造器注入：Spring会自动将所有ILogicTreeNode实现类注入到该Map
     * Key=组件的名称（如@Component("rule_lock")的"rule_lock"）
     */
    public DefaultTreeFactory(Map<String, ILogicTreeNode> logicTreeNodeGroup) {
        this.logicTreeNodeGroup = logicTreeNodeGroup;
    }


    /**
     * 创建决策树引擎实例
     * 设计意图：工厂方法封装引擎创建细节，对外屏蔽DecisionTreeEngine的构造器参数
     *
     * @param ruleTreeVO 规则树配置数据（包含树结构、连线、节点信息）
     * @return 决策树引擎实例
     */
    public IDecisionTreeEngine openLogicTree(RuleTreeVO ruleTreeVO) {
        return new DecisionTreeEngine(logicTreeNodeGroup, ruleTreeVO);
    }

    /**
     * 决策树执行动作实体
     * 用于在各个逻辑节点之间传递执行状态和最终计算结果
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TreeActionEntity {
        /** 规则逻辑校验类型（如：接管、放行） */
        private RuleLogicCheckTypeVO ruleLogicCheckType;
        /** 策略奖品数据（当节点产生最终奖励结果时赋值） */
        private StrategyAwardVO strategyAwardVO;
    }

    /**
     * 策略奖品回调数据实体
     * 封装决策树最终输出的奖品相关信息
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class StrategyAwardVO {
        /** 抽奖奖品ID */
        private Integer awardId;
        /** 抽奖奖品规则扩展值（如：具体金额、消耗积分数等） */
        private String awardRuleValue;
    }

    @Getter
    @AllArgsConstructor
    public enum LogicModel {
        RULE_LUCK_AWARD("rule_luck_award", "保底抽奖"),
        RULE_LOCK("rule_lock", "规则锁"),
        RULE_STOCK("rule_stock", "库存规则"),
        ;

        private final String code;
        private final String info;
    }
}