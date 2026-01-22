package com.c.domain.strategy.service;

import com.c.domain.strategy.model.entity.RaffleAwardEntity;
import com.c.domain.strategy.model.entity.RaffleFactorEntity;

/**
 * @author cyh
 * @description 抽奖策略核心接口
 * @details 定义了抽奖系统的标准行为契约。所有具体的抽奖实现（如标准抽奖、活动抽奖、积分抽奖等）均需遵循此接口规范。
 * 该接口是领域层对外提供的核心服务，隐藏了内部复杂的责任链流转与规则校验细节。
 * @date 2026/01/18
 */
public interface IRaffleStrategy {

    /**
     * 执行抽奖决策
     *
     * @param raffleFactor 抽奖因子实体，包含执行抽奖所必需的物料（如：用户ID、策略ID、抽奖次数等）
     * @return 抽奖结果实体，包含最终命中的奖品ID、奖品名称及相关的状态描述
     */
    RaffleAwardEntity performRaffle(RaffleFactorEntity raffleFactor);

}