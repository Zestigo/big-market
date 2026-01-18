package com.c.domain.strategy.service.rule;

import com.c.domain.strategy.model.entity.RuleActionEntity;
import com.c.domain.strategy.model.entity.RuleMatterEntity;
import org.dom4j.rule.Rule;

/**
 * 抽奖规则过滤接口
 *
 * @author cyh
 * @date 2026/01/17
 */
public interface ILogicFilter<T extends RuleActionEntity.RaffleEntity> {
    RuleActionEntity<T> filter(RuleMatterEntity ruleMatterEntity);
}
