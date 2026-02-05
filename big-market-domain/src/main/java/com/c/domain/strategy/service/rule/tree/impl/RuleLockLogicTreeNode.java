package com.c.domain.strategy.service.rule.tree.impl;

import com.c.domain.strategy.model.vo.RuleLogicCheckTypeVO;
import com.c.domain.strategy.repository.IStrategyRepository;
import com.c.domain.strategy.service.rule.tree.ILogicTreeNode;
import com.c.domain.strategy.service.rule.tree.factory.DefaultTreeFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Date;

/**
 * 规则树节点：次数锁校验 (Rule Lock)
 * * 核心职责：
 * 校验用户在当前策略下的累计抽奖次数是否达到预设阈值，从而决定是否允许抽取该奖品。
 * * 决策逻辑：
 * 1. ALLOW (放行)：用户累计次数 >= 规则阈值，允许继续后续流程（如执行抽奖）。
 * 2. TAKE_OVER (接管)：用户累计次数 < 规则阈值，拦截后续逻辑，锁定奖品。
 * * 节点标识：rule_lock（用于规则树工厂 DefaultTreeFactory 动态路由）
 *
 * @author cyh
 * @date 2026/01/19
 */
@Slf4j
@Component("rule_lock")
public class RuleLockLogicTreeNode implements ILogicTreeNode {

    @Resource
    private IStrategyRepository strategyRepository;

    /**
     * 执行次数锁逻辑判定
     *
     * @param userId     用户ID：用于查询该用户关联的累计抽奖数据
     * @param strategyId 策略ID：对应具体的业务配置方案
     * @param awardId    奖品ID：当前正在校验的奖品对象
     * @param ruleValue  规则阈值：解锁所需的最小抽奖次数（预期为数字字符串，如 "5"）
     * @return 规则树过滤结果实体 (包含 ALLOW 或 TAKE_OVER 状态)
     * @throws IllegalArgumentException 当 ruleValue 配置非数字时抛出，阻止逻辑在错误配置下运行
     */
    @Override
    public DefaultTreeFactory.TreeActionEntity logic(String userId, Long strategyId, Integer awardId,
                                                     String ruleValue, Date endDateTime) {
        log.info("规则树-规则锁节点开始校验 userId:{} strategyId:{} awardId:{} ruleValue:{}", userId, strategyId, awardId,
                ruleValue);

        // 1. 解析阈值：将配置的规则值转换为数值，转换失败则视为配置非法
        long raffleCountThreshold;
        try {
            raffleCountThreshold = Long.parseLong(ruleValue);
        } catch (NumberFormatException e) {
            log.error("规则树-规则锁配置异常，解析 ruleValue 失败: {}", ruleValue);
            throw new IllegalArgumentException("规则锁节点配置非法，预期为数字类型: " + ruleValue);
        }

        // 2. 数据采集：查询用户在当前策略下的累计抽奖次数（通常为当日次数）
        Integer userRaffleCount = strategyRepository.queryTodayUserRaffleCount(userId, strategyId);

        // 3. 判定逻辑：满足阈值则放行，否则接管流程
        if (userRaffleCount >= raffleCountThreshold) {
            log.info("规则树-规则锁校验通过: 用户次数 {} 已达标 {}", userRaffleCount, raffleCountThreshold);
            return DefaultTreeFactory.TreeActionEntity.builder().ruleLogicCheckType(RuleLogicCheckTypeVO.ALLOW).build();
        }

        log.info("规则树-规则锁校验拦截: 用户次数 {} 未达标 {}", userRaffleCount, raffleCountThreshold);
        return DefaultTreeFactory.TreeActionEntity.builder().ruleLogicCheckType(RuleLogicCheckTypeVO.TAKE_OVER).build();
    }
}