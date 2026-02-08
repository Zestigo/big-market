package com.c.domain.strategy.service.rule.chain.factory;

import com.c.domain.strategy.model.entity.StrategyEntity;
import com.c.domain.strategy.repository.IStrategyRepository;
import com.c.domain.strategy.service.rule.chain.ILogicChain;
import lombok.*;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 抽奖责任链工厂
 * 职责：将散落在 Spring 容器中的 ILogicChain 节点，根据策略配置动态组装成有序的执行链路。
 *
 * @author cyh
 * @date 2026/01/18
 */
@Service
public class DefaultChainFactory {

    /** 逻辑链节点映射表 (Bean名称 -> 节点实现) */
    private final Map<String, ILogicChain> logicChainMap;

    /** 策略仓储服务 */
    private final IStrategyRepository strategyRepository;

    public DefaultChainFactory(Map<String, ILogicChain> logicChainMap, IStrategyRepository strategyRepository) {
        this.logicChainMap = logicChainMap;
        this.strategyRepository = strategyRepository;
    }

    /**
     * 根据策略 ID 组装并开启逻辑链路
     * 流程：获取配置 -> 链头初始化 -> 循环串联节点 -> 挂载兜底节点
     *
     * @param strategyId 策略 ID
     * @return 组装完成的责任链头节点
     */
    public ILogicChain openLogicChain(Long strategyId) {
        // 1. 获取策略关联的规则模型（如：blacklist, weight）
        StrategyEntity strategy = strategyRepository.queryStrategyEntityByStrategyId(strategyId);
        String[] ruleModels = strategy.ruleModels();

        // 2. 无规则配置时直接返回默认抽奖节点
        if (null == ruleModels || ruleModels.length == 0) {
            return logicChainMap.get(LogicModel.RULE_DEFAULT.getCode());
        }

        // 3. 链路装配：从首节点开始逐个串联后续节点
        ILogicChain logicChain = logicChainMap.get(ruleModels[0]);
        ILogicChain current = logicChain;

        for (int i = 1; i < ruleModels.length; i++) {
            ILogicChain nextChain = logicChainMap.get(ruleModels[i]);
            current = current.appendNext(nextChain);
        }

        // 4. 链路封口：在业务规则末尾强制挂载默认抽奖逻辑
        current.appendNext(logicChainMap.get(LogicModel.RULE_DEFAULT.getCode()));

        return logicChain;
    }

    /**
     * 策略奖品回调对象
     * 封装责任链最终决策的奖品信息
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class StrategyAwardVO {
        /** 抽奖奖品ID */
        private Integer awardId;
        /** 命中的逻辑模型标识 */
        private String logicModel;
        /** 抽奖奖品规则配置值 */
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
        RULE_WEIGHT("rule_weight", "权重规则"),
        ;

        private final String code;
        private final String info;
    }
}