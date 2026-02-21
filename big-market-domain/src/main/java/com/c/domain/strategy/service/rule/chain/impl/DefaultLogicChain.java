package com.c.domain.strategy.service.rule.chain.impl;

import com.c.domain.strategy.service.armory.IStrategyDispatch;
import com.c.domain.strategy.service.rule.chain.AbstractLogicChain;
import com.c.domain.strategy.service.rule.chain.factory.DefaultChainFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 抽奖责任链 - 默认兜底节点
 * 职责：作为责任链的最末端，执行最终的随机抽奖逻辑，确保用户一定能获得奖品。
 *
 * @author cyh
 * @date 2026/02/21
 */
@Slf4j
@Component("rule_default")
public class DefaultLogicChain extends AbstractLogicChain {

    /* 策略调度服务 */
    @Resource
    protected IStrategyDispatch strategyDispatch;

    /**
     * 执行默认抽奖逻辑
     *
     * @param userId     用户ID
     * @param strategyId 策略ID
     * @return 最终抽取的奖品结果
     */
    @Override
    public DefaultChainFactory.StrategyAwardVO logic(String userId, Long strategyId) {
        // 1. 从默认的全量奖品池中随机获取奖品ID
        Integer awardId = strategyDispatch.getRandomAwardId(strategyId);

        log.info("抽奖责任链-默认兜底节点处理完成 userId: {}, strategyId: {}, awardId: {}", userId, strategyId, awardId);

        // 2. 封装返回结果，作为末端节点不再流转
        return DefaultChainFactory.StrategyAwardVO
                .builder()
                .awardId(awardId)
                .logicModel(ruleModel())
                .build();
    }

    /**
     * 获取规则模型编码
     */
    @Override
    protected String ruleModel() {
        return DefaultChainFactory.LogicModel.RULE_DEFAULT.getCode();
    }
}