package com.c.domain.strategy.service.rule.filter.impl;

import com.c.domain.strategy.model.entity.RuleActionEntity;
import com.c.domain.strategy.model.entity.RuleMatterEntity;
import com.c.domain.strategy.service.annotation.LogicStrategy;
import com.c.domain.strategy.service.rule.filter.ILogicFilter;
import com.c.domain.strategy.service.rule.filter.factory.DefaultLogicFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@LogicStrategy(logicMode = DefaultLogicFactory.LogicModel.RULE_LOCK_AWARD)
public class RuleLockAwardLogicFilter implements ILogicFilter<RuleActionEntity.RaffleCenterEntity> {

    @Override
    public RuleActionEntity<RuleActionEntity.RaffleCenterEntity> filter(RuleMatterEntity ruleMatterEntity) {
        return null;
    }
}
