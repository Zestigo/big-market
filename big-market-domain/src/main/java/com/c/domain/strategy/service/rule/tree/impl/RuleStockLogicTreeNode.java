package com.c.domain.strategy.service.rule.tree.impl;

import com.c.domain.strategy.model.vo.RuleLogicCheckTypeVO;
import com.c.domain.strategy.model.vo.StrategyAwardStockKeyVO;
import com.c.domain.strategy.repository.IStrategyRepository;
import com.c.domain.strategy.service.armory.IStrategyDispatch;
import com.c.domain.strategy.service.rule.tree.ILogicTreeNode;
import com.c.domain.strategy.service.rule.tree.factory.DefaultTreeFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 规则决策树-库存校验节点
 * * 职责：负责在决策链条中进行奖品的实时库存准入判定。
 * 作用：利用 Redis 原子扣减确保库存不超卖。若扣减成功，则发起异步数据库同步请求；若失败，则拦截当前抽奖流程。
 */
@Slf4j
@Component("rule_stock")
public class RuleStockLogicTreeNode implements ILogicTreeNode {

    @Resource
    private IStrategyDispatch strategyDispatch;

    @Resource
    private IStrategyRepository repository;

    /**
     * 执行库存校验与扣减逻辑
     *
     * @param userId     用户唯一ID
     * @param strategyId 策略配置ID
     * @param awardId    经过前置节点计算后，初步命中的奖品ID
     * @param ruleValue  规则配置值（本节点暂未使用）
     * @return TreeActionEntity 包含：
     * - TAKE_OVER: 库存扣减成功，接管后续流程，直接返回奖品信息。
     * - ALLOW: 暂无特定处理流程（本节点通常作为决策终点或拦截点）。
     */
    @Override
    public DefaultTreeFactory.TreeActionEntity logic(String userId, Long strategyId, Integer awardId,
                                                     String ruleValue) {
        log.info("规则树-库存校验节点开始执行: userId:{}, strategyId:{}, awardId:{}", userId, strategyId, awardId);

        // 1. 调用分发服务执行库存扣减（通常基于 Redis Lua 脚本实现原子性）
        Boolean status = strategyDispatch.subtractAwardStock(strategyId, awardId);

        if (status) {
            log.info("规则树-库存校验成功: userId:{}, strategyId:{}, awardId:{}", userId, strategyId, awardId);

            // 2. 扣减成功后，向消息队列或延迟任务发送库存消耗通知，用于异步更新数据库库存记录
            repository.awardStockConsumeSendQueue(StrategyAwardStockKeyVO.builder().strategyId(strategyId)
                                                                         .awardId(awardId).build());

            // 3. 返回接管状态（TAKE_OVER）：表示库存已锁定，此奖品正式归属该用户，不再走后续其他规则节点
            return DefaultTreeFactory.TreeActionEntity.builder()
                                                      .ruleLogicCheckType(RuleLogicCheckTypeVO.TAKE_OVER)
                                                      .strategyAwardVO(DefaultTreeFactory.StrategyAwardVO
                                                              .builder().awardId(awardId)
                                                              .awardRuleValue(ruleValue) // 传递奖品对应的规则配置
                                                              .build()).build();
        }

        // 4. 库存不足（扣减失败）：返回放行状态（ALLOW）
        // 在规则树的设计中，如果当前奖品库存不足，ALLOW 会触发决策树流转到“下一个节点”（通常是兜底奖品节点）
        log.warn("规则树-库存校验失败，奖品已售罄: userId:{}, strategyId:{}, awardId:{}", userId, strategyId, awardId);
        return DefaultTreeFactory.TreeActionEntity.builder().ruleLogicCheckType(RuleLogicCheckTypeVO.ALLOW)
                                                  .build();
    }
}