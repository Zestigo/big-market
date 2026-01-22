package com.c.domain.strategy.service.armory;

/**
 * 策略抽奖调度接口
 * 职责：
 * 1. 执行抽奖策略的随机结果分发。
 * 2. 处理基于权重规则的奖品筛选。
 * 3. 管理奖品库存的原子扣减。
 * 关联说明：调用此接口前，需确保已通过 IStrategyArmory 完成策略数据的预装配。
 */
public interface IStrategyDispatch {

    /**
     * 获取抽奖策略装配的随机结果
     *
     * @param strategyId 策略ID（抽奖配置主键）
     * @return 抽取的奖品ID（AwardId）
     */
    Integer getRandomAwardId(Long strategyId);

    /**
     * 根据策略ID和指定的权重规则获取随机结果
     *
     * @param strategyId      策略ID
     * @param ruleWeightValue 权重规则值（示例："4000:102,103,104" 表示积分达标后的特定奖品池）
     * @return 最终抽取的奖品ID
     */
    Integer getRandomAwardId(Long strategyId, String ruleWeightValue);

    /**
     * 扣减奖品库存
     * 业务逻辑：
     * 通常基于 Redis 结合 Lua 脚本实现。
     * 确保在高并发场景下，奖品库存的扣减是原子性的，防止超卖。
     *
     * @param strategyId 策略ID
     * @param awardId    奖品ID
     * @return 是否扣减成功（true: 扣减成功且有余量；false: 库存不足或扣减失败）
     */
    Boolean subtractAwardStock(Long strategyId, Integer awardId);

}