package com.c.domain.strategy.service.rule.tree.impl;

import com.c.domain.strategy.model.vo.RuleLogicCheckTypeVO;
import com.c.domain.strategy.repository.IStrategyRepository;
import com.c.domain.strategy.service.rule.tree.ILogicTreeNode;
import com.c.domain.strategy.service.rule.tree.factory.DefaultTreeFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 规则树-规则锁逻辑节点
 * 核心职责：
 * 1. 校验用户抽奖行为是否满足预设的规则锁条件（核心为抽奖次数阈值校验）
 * 2. 根据校验结果决定是否解锁奖品：满足条件则允许继续后续流程，不满足则拦截流程
 * 3. 典型应用场景：用户累计抽奖次数达到指定阈值后，解锁特定奖品的抽取权限
 * 节点标识：rule_lock（通过@Component注解声明，供规则树工厂动态加载）
 *
 * @author cyh
 * @date 2026/01/19
 */
@Slf4j
@Component("rule_lock")
public class RuleLockLogicTreeNode implements ILogicTreeNode {

    @Resource
    private IStrategyRepository strategyRepository;

    // 临时测试用：模拟用户累计抽奖次数，实际业务中需从strategyRepository查询真实数据
    // TODO: 替换为从仓储层获取用户真实抽奖次数的业务逻辑
    private long userRaffleCount = 10L;

    /**
     * 执行规则锁的核心判定逻辑
     * 核心逻辑：
     * 1. 解析规则值（ruleValue）：需为数字格式，代表解锁奖品的最低抽奖次数阈值
     * 2. 规则值解析失败时抛出运行时异常，提示配置错误
     * 3. 对比用户累计抽奖次数与阈值：
     * - 达到/超过阈值：返回ALLOW，解锁奖品，继续后续规则树流程
     * - 未达到阈值：返回TAKE_OVER，拦截流程，锁定奖品
     *
     * @param userId     用户唯一标识，用于查询该用户的累计抽奖次数
     * @param strategyId 策略ID，关联具体的抽奖策略配置
     * @param awardId    当前进行锁校验的目标奖品ID
     * @param ruleValue  规则配置值，格式要求为数字（解锁奖品的最低抽奖次数），例如 "5"
     * @return DefaultTreeFactory.TreeActionEntity 规则树决策结果实体
     * - ALLOW：用户抽奖次数达标，解锁奖品，继续后续节点判定
     * - TAKE_OVER：用户抽奖次数未达标，锁定奖品，终止后续流程
     * @throws RuntimeException 当ruleValue非数字格式时抛出，提示规则值配置错误
     */
    @Override
    public DefaultTreeFactory.TreeActionEntity logic(String userId, Long strategyId, Integer awardId,
                                                     String ruleValue) {
        // 日志记录规则锁校验开始，便于链路追踪和问题排查
        log.info("规则树-规则锁节点执行校验: userId={}, strategyId={}, awardId={}, ruleValue={}", userId, strategyId,
                awardId, ruleValue);

        // 解析规则值为抽奖次数阈值，需确保为数字格式
        long raffleCountThreshold;
        try {
            raffleCountThreshold = Long.parseLong(ruleValue);
        } catch (Exception e) {
            log.error("规则树-规则锁节点解析异常：规则值非数字格式，userId={}, strategyId={}, awardId={}, ruleValue={}", userId,
                    strategyId, awardId, ruleValue, e);
            throw new RuntimeException("规则锁节点规则值配置错误，需传入数字格式的抽奖次数阈值：" + ruleValue);
        }

        // 校验用户累计抽奖次数是否达到解锁阈值
        if (userRaffleCount >= raffleCountThreshold) {
            log.info("规则树-规则锁节点校验通过：用户抽奖次数达标，解锁奖品，userId={}, awardId={}, 累计次数={}, 阈值={}", userId, awardId,
                    userRaffleCount, raffleCountThreshold);
            // 次数达标，返回ALLOW，允许继续后续流程
            return DefaultTreeFactory.TreeActionEntity.builder()
                                                      .ruleLogicCheckType(RuleLogicCheckTypeVO.ALLOW).build();
        }

        log.info("规则树-规则锁节点校验失败：用户抽奖次数未达标，锁定奖品，userId={}, awardId={}, 累计次数={}, 阈值={}", userId, awardId,
                userRaffleCount, raffleCountThreshold);
        // 次数未达标，返回TAKE_OVER，拦截流程
        return DefaultTreeFactory.TreeActionEntity.builder()
                                                  .ruleLogicCheckType(RuleLogicCheckTypeVO.TAKE_OVER).build();
    }
}