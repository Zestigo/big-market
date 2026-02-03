package com.c.domain.strategy.service.armory;

/**
 * 策略装配库接口（策略武器库）
 * * 职责：
 * 负责将数据库中的抽奖策略（奖品、概率、规则等）进行预热和转换。
 * 通过提前计算概率查找表并缓存至持久化层（如 Redis），确保抽奖调度时具备极高的响应性能。
 */
public interface IStrategyArmory {

    /**
     * 装配抽奖策略
     * * 核心逻辑：
     * 1. 加载策略关联的奖品配置及概率。
     * 2. 计算并生成高性能随机查找表（空间换时间算法）。
     * 3. 预热奖品库存至缓存。
     * 4. 存储权重规则对应的子奖池映射。
     *
     * @param strategyId 策略ID（对应 strategy 实体主键）
     * @return 装配结果（true: 装配成功；false: 装配失败，可能由于配置缺失或存储异常）
     */
    boolean assembleLotteryStrategy(Long strategyId);

    boolean assembleLotteryStrategyByActivityId(Long activityId);
}