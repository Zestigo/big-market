package com.c.domain.strategy.repository;

import com.c.domain.strategy.model.entity.StrategyAwardEntity;

import java.util.HashMap;
import java.util.List;

public interface IStrategyRepository {
    // 根据策略ID查询奖项列表
    List<StrategyAwardEntity> queryStrategyAwardList(Long strategyId);

    // 存储概率查找表到Redis（key=索引，value=奖品ID）
    void storeStrategyAwardSearchRateTables(Long strategyId, Integer rateRange, HashMap<Integer, Integer> shuffleStrategyAwardSearchRateTables);

    // 从Redis获取概率范围（比如1000=总共有1000个抽奖索引）
    int getRateRange(Long strategyId);

    // 根据随机索引从Redis获取奖品ID
    Integer getStrategyAwardAssemble(Long strategyId, int rateKey);
}