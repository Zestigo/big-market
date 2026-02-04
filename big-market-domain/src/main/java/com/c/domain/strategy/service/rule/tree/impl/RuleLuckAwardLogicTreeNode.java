package com.c.domain.strategy.service.rule.tree.impl;

import com.c.domain.strategy.model.vo.RuleLogicCheckTypeVO;
import com.c.domain.strategy.service.rule.tree.ILogicTreeNode;
import com.c.domain.strategy.service.rule.tree.factory.DefaultTreeFactory;
import com.c.types.common.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * 规则树-幸运奖励逻辑节点
 * 核心职责：
 * 1. 作为策略规则决策树的兜底节点或特定幸运奖励路径节点
 * 2. 执行时直接接管整个决策流程，终止后续节点的判定逻辑
 * 3. 根据配置的规则值（ruleValue）解析并返回固定的幸运奖品信息
 * 节点标识：rule_luck_award（通过@Component注解声明，供规则树工厂动态加载）
 *
 * @author cyh
 * @date 2026/01/19
 */
@Slf4j
@Component("rule_luck_award")
public class RuleLuckAwardLogicTreeNode implements ILogicTreeNode {

    /**
     * 执行幸运奖发放的规则判定逻辑
     * 核心逻辑：
     * 1. 解析规则值（ruleValue）：格式为「奖品ID[:奖品规则值]」，冒号分隔，后者为可选参数
     * 2. 若规则值解析失败（无有效内容），抛出运行时异常，触发配置告警
     * 3. 封装幸运奖品信息，返回「接管（TAKE_OVER）」类型的决策结果，终止规则树后续流程
     *
     * @param userId     用户唯一标识，用于日志追踪和用户维度的规则判定
     * @param strategyId 策略ID，关联具体的抽奖策略配置
     * @param awardId    当前流转到该节点的奖品ID（会被幸运奖配置的奖品ID替换）
     * @param ruleValue  规则配置值，格式要求：奖品ID[:奖品规则值]，例如 "1001" 或 "1001:limit_1"
     * @return DefaultTreeFactory.TreeActionEntity 规则树决策结果实体
     * - ruleLogicCheckType：固定为TAKE_OVER（接管流程）
     * - strategyAwardVO：封装最终发放的幸运奖品ID和奖品规则值
     * @throws RuntimeException 当ruleValue为空或解析不出有效奖品ID时抛出，提示兜底奖品配置异常
     */
    @Override
    public DefaultTreeFactory.TreeActionEntity logic(String userId, Long strategyId, Integer awardId,
                                                     String ruleValue, Date endDateTime) {
        // 日志记录节点执行入参，便于问题排查和链路追踪
        log.info("规则树-幸运奖节点执行开始: userId={}, strategyId={}, currentAwardId={}, ruleValue={}", userId,
                strategyId, awardId, ruleValue);

        // 解析规则值：按系统常量的冒号分隔，拆分出奖品ID和扩展规则值
        String[] split = ruleValue.split(Constants.COLON);
        // 规则值解析校验：无有效内容则抛出异常并记录告警日志
        if (split.length == 0) {
            log.error("规则树-幸运奖节点配置异常：兜底奖品未配置，userId={}, strategyId={}, currentAwardId={}, " + "ruleValue" + "={}", userId, strategyId, awardId, ruleValue);
            throw new RuntimeException("幸运奖节点规则值配置异常，未解析到有效奖品ID：" + ruleValue);
        }

        // 覆盖原奖品ID为幸运奖配置的奖品ID
        Integer luckAwardId = Integer.parseInt(split[0]);
        // 解析可选的奖品扩展规则值，无则赋值为空字符串
        String luckAwardRuleValue = split.length > 1 ? split[1] : "";

        // 日志记录解析后的幸运奖品信息，便于核对配置是否生效
        log.info("规则树-幸运奖节点执行完成：已解析幸运奖品信息，userId={}, strategyId={}, luckAwardId={}, " + "luckAwardRuleValue"
                + "={}", userId, strategyId, luckAwardId, luckAwardRuleValue);

        // 构建决策结果：接管规则树流程，并返回最终的幸运奖品配置
        return DefaultTreeFactory.TreeActionEntity.builder()
                                                  .ruleLogicCheckType(RuleLogicCheckTypeVO.TAKE_OVER)
                                                  .strategyAwardVO(DefaultTreeFactory.StrategyAwardVO
                                                          .builder().awardId(luckAwardId)
                                                          .awardRuleValue(luckAwardRuleValue).build())
                                                  .build();
    }

}