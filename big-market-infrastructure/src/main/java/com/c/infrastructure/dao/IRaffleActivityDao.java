package com.c.infrastructure.dao;

import com.c.infrastructure.po.RaffleActivity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 抽奖活动基础配置数据访问接口
 * 职责：
 * 负责检索活动的核心元数据（如活动名称、状态、起止时间、关联策略等）。
 * 该接口是构建活动领域聚合根（Activity Aggregate）的基础数据源。
 *
 * @author cyh
 * @date 2026/01/29
 */
@Mapper
public interface IRaffleActivityDao {

    /**
     * 根据活动ID查询活动基础配置信息
     * 业务用途：
     * 1. 在责任链准入校验（ActivityBaseActionChain）中，用于判定活动是否在有效期内及状态是否开启。
     * 2. 在抽奖过程中，获取该活动绑定的策略ID（strategyId），以决定后续的奖品算法。
     *
     * @param activityId 活动唯一标识
     * @return 抽奖活动基础配置持久化对象
     */
    RaffleActivity queryRaffleActivityByActivityId(Long activityId);

}