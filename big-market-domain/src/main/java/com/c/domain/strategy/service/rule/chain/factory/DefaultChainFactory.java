package com.c.domain.strategy.service.rule.chain.factory;

import com.c.domain.strategy.model.entity.StrategyEntity;
import com.c.domain.strategy.repository.IStrategyRepository;
import com.c.domain.strategy.service.rule.chain.ILogicChain;
import lombok.*;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 抽奖责任链工厂
 * 职责：根据策略配置动态组装执行链路，确保业务规则有序执行并最终由默认节点兜底。
 *
 * @author cyh
 * @date 2026/02/21
 */
@Service
public class DefaultChainFactory {

    /* 逻辑链节点映射表 (Bean名称 -> 节点实现) */
    private final Map<String, ILogicChain> logicChainMap;

    /* 策略仓储服务 */
    private final IStrategyRepository strategyRepository;

    public DefaultChainFactory(Map<String, ILogicChain> logicChainMap, IStrategyRepository strategyRepository) {
        this.logicChainMap = logicChainMap;
        this.strategyRepository = strategyRepository;
    }

    /**
     * 装配并开启逻辑链路
     *
     * @param strategyId 策略ID
     * @return 组装完成的责任链首节点
     */
    public ILogicChain openLogicChain(Long strategyId) {
        StrategyEntity strategy = strategyRepository.queryStrategyEntityByStrategyId(strategyId);
        String[] ruleModels = strategy.ruleModels();

        // 准备兜底节点（默认抽奖逻辑）
        ILogicChain defaultChain = logicChainMap.get(LogicModel.RULE_DEFAULT.getCode());

        // 1. 无业务规则配置时，直接返回兜底节点
        if (null == ruleModels || ruleModels.length == 0) return defaultChain;

        // 2. 链路初始化：以配置的第一个规则作为首节点
        ILogicChain head = logicChainMap.get(ruleModels[0]);
        ILogicChain current = head;

        // 3. 循环挂载：逐个串联后续规则节点，并实时更新 current 指针
        for (int i = 1; i < ruleModels.length; i++) {
            ILogicChain nextChain = logicChainMap.get(ruleModels[i]);
            current = current.appendNext(nextChain);
        }

        // 4. 链路封口：在所有业务逻辑末尾强制挂载默认抽奖逻辑，防止链条断裂
        current.appendNext(defaultChain);

        return head;
    }

    /**
     * 策略奖品回调对象
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class StrategyAwardVO {
        /* 抽奖奖品ID */
        private Integer awardId;
        /* 命中的逻辑模型标识 */
        private String logicModel;
        /* 抽奖奖品规则配置值 */
        private String awardRuleValue;
    }

    /**
     * 逻辑模型枚举
     */
    @Getter
    @AllArgsConstructor
    public enum LogicModel {
        RULE_DEFAULT("rule_default", "默认抽奖"),
        RULE_BLACKLIST("rule_blacklist", "黑名单规则"),
        RULE_WEIGHT("rule_weight", "权重规则");

        /* 模型编码 */
        private final String code;
        /* 模型描述 */
        private final String info;
    }
}