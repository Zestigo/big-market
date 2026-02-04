package com.c.domain.strategy.service;

import com.c.domain.strategy.model.entity.StrategyAwardEntity;
import java.util.List;

/**
 * 抽奖奖品服务接口
 * 负责提供不同维度的抽奖奖品配置查询能力
 *
 * @author cyh
 * @since 2026/01/23
 */
public interface IRaffleAward {

    /**
     * 根据策略 ID 查询抽奖奖品列表配置
     * 常用于策略领域内部或已获取策略 ID 的场景
     *
     * @param strategyId 策略 ID
     * @return 奖品实体列表
     */
    List<StrategyAwardEntity> queryRaffleStrategyAwardList(Long strategyId);

    /**
     * 根据活动 ID 查询抽奖奖品列表配置
     * 涉及跨领域转换：由活动 ID 路由至关联的策略 ID，再获取奖品清单
     *
     * @param activityId 活动 ID
     * @return 奖品实体列表
     */
    List<StrategyAwardEntity> queryRaffleStrategyAwardListByActivityId(Long activityId);
}