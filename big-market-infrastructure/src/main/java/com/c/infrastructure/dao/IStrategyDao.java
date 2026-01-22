package com.c.infrastructure.dao;

import com.c.domain.strategy.model.entity.StrategyEntity;
import com.c.infrastructure.dao.po.Strategy;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface IStrategyDao {
    List<Strategy> queryStrategyList();

    Strategy queryStrategyEntityByStrategyId(Long strategyId);
}
