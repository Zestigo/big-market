package com.c.domain.strategy.service.armory;

public interface IStrategyArmory {
    // 装配抽奖策略（计算概率表并存入Redis）
    boolean assembleLotteryStrategy(Long strategyId);
}