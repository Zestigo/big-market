package com.c.infrastructure.dao;

import com.c.infrastructure.po.StrategyRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 策略规则配置查询 DAO
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
     * 业务用途：通常用于系统启动时的规则预热或后台规则配置管理。
     *
     * @return 策略规则持久化对象列表 (List<StrategyRule>)
     */
    List<StrategyRule> queryStrategyRuleList();

    /**
     * 查询特定规则的配置值 (Rule Value)
     * 业务逻辑：获取具体的规则参数。
     * 1. 权重规则示例：1000:102,103 (表示积分满1000可抽奖品集合)
     * 2. 黑名单规则示例：user01,user02 (表示受限用户名单)
     *
     * @param strategyRule 包含 strategyId(必填)、ruleModel(必填)、awardId(可选) 的查询实体
     * @return 规则配置的具体字符串值 (如: "4000:101,102")
     */
    String queryStrategyRuleValue(StrategyRule strategyRule);

    /**
     * 查询完整的策略规则实体
     * 业务逻辑：在职责链执行过程中，获取规则的类型(rule_type)、描述及配置值。
     * 帮助逻辑处理器 (LogicHandler) 判断该规则是属于“早回(拦截)”还是“放行”。
     *
     * @param strategyRule 包含 strategyId(必填)、ruleModel(必填) 的查询实体
     * @return 策略规则持久化对象 (StrategyRule PO)
     */
    StrategyRule queryStrategyRule(StrategyRule strategyRule);

}