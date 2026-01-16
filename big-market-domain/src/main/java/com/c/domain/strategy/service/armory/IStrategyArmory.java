package com.c.domain.strategy.service.armory;

public interface IStrategyArmory {
    // 装配抽奖策略（计算概率表并存入Redis）
    void assembleLotteryStrategy(Long strategyId);

    // 随机获取奖品ID（核心抽奖逻辑）
    Integer getRandomAwardId(Long strategyId);
}