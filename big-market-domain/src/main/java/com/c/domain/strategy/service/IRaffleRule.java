package com.c.domain.strategy.service;

import com.c.domain.strategy.model.vo.RuleWeightVO;
import java.util.List;
import java.util.Map;

/**
 * 抽奖规则服务接口
 * 负责查询与抽奖逻辑相关的规则配置，如奖品解锁门槛、次数限制等
 *
 * @author cyh
 * @since 2026/02/04
 */
public interface IRaffleRule {

    /**
     * 批量查询奖品规则锁定次数配置
     *
     * @param treeIds 规则树 ID 集合（对应各奖品配置的规则树）
     * @return Map 映射（Key: 规则树 ID, Value: 解锁所需抽奖次数）
     */
    Map<String, Integer> queryAwardRuleLockCount(String[] treeIds);

    /**
     * 根据策略 ID 查询权重规则配置
     *
     * @param strategyId 策略 ID
     * @return 权重规则配置列表
     */
    List<RuleWeightVO> queryAwardRuleWeight(Long strategyId);

    /**
     * 根据活动 ID 查询权重规则配置
     *
     * @param activityId 活动 ID
     * @return 权重规则配置列表
     */
    List<RuleWeightVO> queryAwardRuleWeightByActivityId(Long activityId);

}