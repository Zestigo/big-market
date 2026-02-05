package com.c.domain.strategy.service.armory;

import java.util.Date;

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
     * 原子扣减奖品库存（Redis Lua 异步化方案）
     * * 核心逻辑：
     * 1. 采用 Redis + Lua 脚本实现原子性判定与扣减，确保高并发下不发生“超卖”。
     * 2. 扣减成功后，需同步将库存消耗记录塞入延迟队列（如本地 DelayQueue 或消息队列），
     * 以便后续异步更新数据库物理库存，实现最终一致性。
     *
     * @param strategyId  策略 ID
     * @param awardId     奖品 ID
     * @param endDateTime 活动结束时间（用于设置缓存 Key 的过期时长，防止僵尸数据堆积）
     * @return 扣减结果：
     * - true:  库存充足且扣减成功，允许发放奖品。
     * - false: 库存不足或已售罄，需执行拦截或走兜底逻辑。
     */
    Boolean subtractAwardStock(Long strategyId, Integer awardId, Date endDateTime);
}