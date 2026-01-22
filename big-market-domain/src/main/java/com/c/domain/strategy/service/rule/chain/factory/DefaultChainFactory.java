package com.c.domain.strategy.service.rule.chain.factory;

import com.c.domain.strategy.model.entity.RuleActionEntity;
import com.c.domain.strategy.model.entity.StrategyEntity;
import com.c.domain.strategy.repository.IStrategyRepository;
import com.c.domain.strategy.service.rule.chain.ILogicChain;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * @author cyh
 * @description 责任链工厂：动态组装抽奖逻辑链路
 * @details 核心功能是根据策略 ID 从数据库/缓存中获取配置的规则模型（rule_models），
 * 并通过 Spring 注入的 Bean 集合，将这些规则节点按顺序装配成一条可执行的责任链。
 * 这种方式实现了规则的灵活插拔，无需硬编码即可调整抽奖逻辑。
 * @date 2026/01/18
 */
@Service
public class DefaultChainFactory {

    /** 逻辑链节点映射表：由 Spring 自动注入，Key 为 Bean 名称（如 "rule_weight"），Value 为具体实现节点 */
    private final Map<String, ILogicChain> logicChainMap;

    /** 策略仓储服务：用于获取策略对应的规则配置元数据 */
    private final IStrategyRepository strategyRepository;

    public DefaultChainFactory(Map<String, ILogicChain> logicChainMap, IStrategyRepository strategyRepository) {
        this.logicChainMap = logicChainMap;
        this.strategyRepository = strategyRepository;
    }

    /**
     * 根据策略 ID 开启并组装逻辑链路
     * 组装逻辑：
     * 1. 检索策略配置，获取规则模型数组（例如：["rule_blacklist", "rule_weight"]）。
     * 2. 依次从 Bean Map 中取出对应的节点进行首尾相连。
     * 3. 强制在链条末尾挂载 "default" 兜底节点。
     *
     * @param strategyId 策略 ID
     * @return 组装完成的责任链头节点
     */
    public ILogicChain openLogicChain(Long strategyId) {
        // 1. 查询策略实体，获取其关联的规则模型列表
        StrategyEntity strategy = strategyRepository.queryStrategyEntityByStrategyId(strategyId);
        String[] ruleModels = strategy.ruleModels();

        // 2. 边界处理：若未配置任何规则，则直接返回默认兜底抽奖节点
        if (null == ruleModels || ruleModels.length == 0) {
            return logicChainMap.get("default");
        }

        // 3. 初始化链路：取出第一个规则节点作为链头
        ILogicChain logicChain = logicChainMap.get(ruleModels[0]);
        ILogicChain current = logicChain;

        // 4. 循环装配：遍历后续规则，通过 appendNext 将节点逐一串联
        for (int i = 1; i < ruleModels.length; i++) {
            ILogicChain nextChain = logicChainMap.get(ruleModels[i]);
            current = current.appendNext(nextChain);
        }

        // 5. 链路封口：在所有业务规则执行完毕后，必须指向 default 兜底抽奖逻辑
        current.appendNext(logicChainMap.get("default"));

        return logicChain;
    }
}