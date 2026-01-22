package com.c.domain.strategy.service.rule.chain.impl;

import com.c.domain.strategy.service.armory.IStrategyDispatch;
import com.c.domain.strategy.service.rule.chain.AbstractLogicChain;
import com.c.domain.strategy.service.rule.chain.factory.DefaultChainFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * @description 抽奖责任链 - 默认兜底节点
 * @details 该节点通常位于责任链的最末端。当所有前置规则（如黑名单、权重、会员等级等）均未命中或已完成校验后，
 * 由此节点执行最终的随机抽奖逻辑，确保用户一定能获得一个基础奖品池内的奖品。
 * * @author cyh
 * @date 2026/01/18
 */
@Slf4j
@Component("default")
public class DefaultLogicChain extends AbstractLogicChain {

    @Resource
    protected IStrategyDispatch strategyDispatch;

    /**
     * 执行默认抽奖逻辑
     *
     * @param userId     用户ID
     * @param strategyId 策略ID
     * @return 最终抽取的奖品ID。作为兜底节点，此逻辑不再调用 next()，直接返回结果。
     */
    @Override
    public DefaultChainFactory.StrategyAwardVO  logic(String userId, Long strategyId) {
        // 从默认的全量/基础奖品池中随机获取奖品ID
        Integer awardId = strategyDispatch.getRandomAwardId(strategyId);
        log.info("抽奖责任链-默认兜底节点处理完成 userId: {}, strategyId: {}, awardId: {}", userId, strategyId, awardId);
        return DefaultChainFactory.StrategyAwardVO.builder().awardId(awardId).logicModel(ruleModel()).build();
    }

    /**
     * 获取规则模型编码
     *
     * @return 默认规则标识
     */
    @Override
    protected String ruleModel() {
        return DefaultChainFactory.LogicModel.RULE_DEFAULT.getCode();
    }
}