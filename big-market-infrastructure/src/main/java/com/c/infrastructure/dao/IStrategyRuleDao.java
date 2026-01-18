package com.c.infrastructure.dao;

import com.c.infrastructure.dao.po.StrategyRule;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface IStrategyRuleDao {
    List<StrategyRule> queryStrategyRuleList();

    String queryStrategyRuleValue(StrategyRule strategyRule);

    StrategyRule queryStrategyRule(StrategyRule strategyRule);
}
