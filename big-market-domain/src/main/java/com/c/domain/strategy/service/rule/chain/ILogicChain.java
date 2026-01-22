package com.c.domain.strategy.service.rule.chain;

import com.c.domain.strategy.service.rule.chain.factory.DefaultChainFactory;

/**
 * 抽奖策略规则过滤责任链接口
 * *该接口定义了责任链模式的标准行为：
 * 1. 执行当前节点的业务逻辑处理。
 * 2. 动态组装链条节点。
 * 3. 获取并指向链条中的下一个执行节点。</p>
 *
 * @author cyh
 * @date 2026/01/18
 */
public interface ILogicChain extends ILogicChainArmory {

    /**
     * 责任链逻辑处理过滤
     *
     * @param userId     用户ID：用于唯一标识用户，进行黑名单或权重过滤
     * @param strategyId 策略ID：对应具体的抽奖配置
     * @return 奖品ID。若当前节点无法决策，通常由下一个节点返回；若被拦截，则返回特定奖品或 NULL。
     */
    DefaultChainFactory.StrategyAwardVO logic(String userId, Long strategyId);

}