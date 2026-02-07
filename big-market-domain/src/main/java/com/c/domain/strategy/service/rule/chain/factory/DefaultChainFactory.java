package com.c.domain.strategy.service.rule.chain.factory;

import com.c.domain.strategy.model.entity.StrategyEntity;
import com.c.domain.strategy.repository.IStrategyRepository;
import com.c.domain.strategy.service.rule.chain.ILogicChain;
import lombok.*;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 抽奖责任链工厂
 * 描述：负责将散落在 Spring 容器中的逻辑节点（ILogicChain）根据策略配置动态组装成有序链路。
 *
 * @author cyh
 * @date 2026/01/18
 */
@Service
public class DefaultChainFactory {

    /** 逻辑链节点映射表 (Key: Bean名称, Value: 逻辑链节点实现) */
    private final Map<String, ILogicChain> logicChainMap;

    /** 策略仓储服务 */
    private final IStrategyRepository strategyRepository;

    /**
     * 构造函数：注入 Spring 托管的逻辑节点 Map 及仓储服务
     *
     * @param logicChainMap      逻辑链节点映射
     * @param strategyRepository 策略仓储
     */
    public DefaultChainFactory(Map<String, ILogicChain> logicChainMap, IStrategyRepository strategyRepository) {
        this.logicChainMap = logicChainMap;
        this.strategyRepository = strategyRepository;
    }

    /**
     * 根据策略 ID 开启并动态组装逻辑链路
     * 组装逻辑：1.获取配置规则 -> 2.首节点初始化 -> 3.循环串联 -> 4.挂载兜底节点
     *
     * @param strategyId 策略 ID
     * @return 组装完成的责任链头节点
     */
    public ILogicChain openLogicChain(Long strategyId) {
        // 1. 查询策略实体，获取其关联的规则模型列表 (如: ["rule_blacklist", "rule_weight"])
        StrategyEntity strategy = strategyRepository.queryStrategyEntityByStrategyId(strategyId);
        String[] ruleModels = strategy.ruleModels();

        // 2. 边界处理：若未配置任何规则，则直接返回默认兜底抽奖节点
        if (null == ruleModels || ruleModels.length == 0) {
            return logicChainMap.get(LogicModel.RULE_DEFAULT.getCode());
        }

        // 3. 链路初始化：取出配置的第一个规则节点作为链头
        ILogicChain logicChain = logicChainMap.get(ruleModels[0]);
        ILogicChain current = logicChain;

        // 4. 循环装配：遍历后续规则模型，通过 appendNext 将节点逐一串联
        for (int i = 1; i < ruleModels.length; i++) {
            ILogicChain nextChain = logicChainMap.get(ruleModels[i]);
            current = current.appendNext(nextChain);
        }

        // 5. 链路封口：在所有业务规则执行完毕后，强制挂载默认兜底抽奖逻辑
        current.appendNext(logicChainMap.get(LogicModel.RULE_DEFAULT.getCode()));

        return logicChain;
    }

    /**
     * 策略奖品回调数据实体
     * 封装责任链决策后的奖品相关信息
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
    }

    /**
     * 逻辑模型枚举
     * 定义责任链中各节点的唯一标识及描述
     */
    @Getter
    @AllArgsConstructor
    public enum LogicModel {
        /** 默认抽奖规则 */
        RULE_DEFAULT("rule_default", "默认抽奖"),
        /** 黑名单校验规则 */
        RULE_BLACKLIST("rule_blacklist", "黑名单规则"),
        /** 权重阶梯校验规则 */
        RULE_WEIGHT("rule_weight", "权重规则"),
        ;

        /** 规则代码 */
        private final String code;
        /** 规则描述 */
        private final String info;
    }
}