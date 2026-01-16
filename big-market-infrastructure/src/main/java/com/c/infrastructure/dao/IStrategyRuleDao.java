package com.c.infrastructure.dao;

import com.c.infrastructure.dao.po.Strategy;
import com.c.infrastructure.dao.po.StrategyRule;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 策略规则道
 *
 * @author cyh
 * @date 2026/01/15
 */
@Mapper
public interface IStrategyRuleDao {
    List<StrategyRule> queryStrategyRuleList();
}
