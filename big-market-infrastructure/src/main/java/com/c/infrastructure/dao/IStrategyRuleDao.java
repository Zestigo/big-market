package com.c.infrastructure.dao;

import com.c.infrastructure.po.StrategyRule;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 策略规则配置查询 DAO
 * * 职责：
 * 负责访问 `strategy_rule` 表，获取抽奖过程中的各类业务规则配置。
 * 包含但不限于：黑名单规则、权重规则、中奖概率倍率规则等。
 *
 * @author cyh
 * @date 2026/01/21
 */
@Mapper
public interface IStrategyRuleDao {

    /**
     * 查询全量策略规则列表
     * * 业务用途：通常用于系统启动时的规则预热或后台规则配置管理。
     *
     * @return 策略规则持久化对象列表
     */
    List<StrategyRule> queryStrategyRuleList();

    /**
     * 查询特定规则的配置值（Rule Value）
     * * 业务逻辑：
     * 用于获取具体的规则参数。例如：
     * 1. 权重规则下：1000:102,103 (表示积分满1000可抽102,103奖品)
     * 2. 黑名单规则下：user01,user02 (表示这些用户被拦截)
     *
     * @param strategyRule 包含 strategyId、awardId(可选)、ruleModel 的查询条件
     * @return 规则配置的具体字符串值
     */
    String queryStrategyRuleValue(StrategyRule strategyRule);

    /**
     * 查询完整的策略规则实体
     * * 业务逻辑：
     * 在职责链执行过程中，获取规则的类型（rule_type）、描述及配置值。
     * 帮助逻辑处理器（LogicHandler）判断该规则是属于“早回（拦截）”还是“放行”。
     *
     *
     *
     * @param strategyRule 包含 strategyId、ruleModel 的查询条件
     * @return 策略规则持久化对象
     */
    StrategyRule queryStrategyRule(StrategyRule strategyRule);

}