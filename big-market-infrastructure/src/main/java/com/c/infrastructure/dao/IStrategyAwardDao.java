package com.c.infrastructure.dao;

import com.c.infrastructure.dao.po.StrategyAward;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper // MyBatis映射注解
public interface IStrategyAwardDao {
    // 查询所有奖项（示例）
    List<StrategyAward> queryStrategyAwardList();

    // 根据策略ID查询奖项
    List<StrategyAward> queryStrategyAwardListByStrategyId(Long strategyId);

    String queryStrategyAwardRuleModel();
}