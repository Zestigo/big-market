package com.c.domain.strategy.service.rule.filter.impl;

import com.c.domain.strategy.model.entity.RuleActionEntity;
import com.c.domain.strategy.model.entity.RuleMatterEntity;
import com.c.domain.strategy.model.vo.RuleLogicCheckTypeVO;
import com.c.domain.strategy.repository.IStrategyRepository;
import com.c.domain.strategy.service.annotation.LogicStrategy;
import com.c.domain.strategy.service.rule.filter.ILogicFilter;
import com.c.domain.strategy.service.rule.filter.factory.DefaultLogicFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Slf4j
@Component
@LogicStrategy(logicMode = DefaultLogicFactory.LogicModel.RULE_LOCK)
public class RuleLockLogicFilter implements ILogicFilter<RuleActionEntity.RaffleCenterEntity> {
    @Resource
    private IStrategyRepository strategyRepository;
    /** 用户抽奖次数 */
    private Long userRaffleCount = 0L;

    @Override
    public RuleActionEntity<RuleActionEntity.RaffleCenterEntity> filter(RuleMatterEntity ruleMatterEntity) {
        log.info("规则过滤-次数锁 userId:{} strategyId:{} ruleModel:{}", ruleMatterEntity.getUserId(),
                ruleMatterEntity.getStrategyId(), ruleMatterEntity.getRuleModel());
        long ruleValue = Long.parseLong(strategyRepository.queryStrategyRuleValue(ruleMatterEntity.getStrategyId(),
                ruleMatterEntity.getAwardId(), ruleMatterEntity.getRuleModel()));
        log.info("规则中rule_lock:{}", ruleValue);
        if (userRaffleCount >= ruleValue) {
            return RuleActionEntity.<RuleActionEntity.RaffleCenterEntity>builder().code(RuleLogicCheckTypeVO.ALLOW.getCode())
                                   .info(RuleLogicCheckTypeVO.ALLOW.getInfo()).build();
        }
        return RuleActionEntity.<RuleActionEntity.RaffleCenterEntity>builder().code(RuleLogicCheckTypeVO.TAKE_OVER.getCode())
                               .info(RuleLogicCheckTypeVO.TAKE_OVER.getInfo()).build();
    }
}
