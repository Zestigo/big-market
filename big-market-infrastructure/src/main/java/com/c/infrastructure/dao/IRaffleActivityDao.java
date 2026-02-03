package com.c.infrastructure.dao;

import com.c.infrastructure.po.RaffleActivity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 抽奖活动基础配置数据访问接口 (Data Access Object)
 * 1. 基础数据检索：映射 `raffle_activity` 表，获取活动起止时间、状态、关联策略等元数据。
 * 2. 领域建模支持：作为活动领域（Activity Domain）的基础设施实现，支撑活动聚合根的构建。
 * 3. 跨域关联：提供“活动-策略”的双向索引查询，确保业务链路的逻辑闭环。
 *
 * @author cyh
 * @date 2026/01/29
 */
@Mapper
public interface IRaffleActivityDao {

    /**
     * 根据活动ID查询活动基础配置信息
     * * 典型场景：
     * - 校验阶段：判断当前时间是否在活动有效期（begin_time ~ end_time）内。
     * - 准入阶段：检查活动当前状态（state）是否为“开启”或“进行中”。
     *
     * @param activityId 活动主键 ID
     * @return 抽奖活动持久化对象 (PO)
     */
    RaffleActivity queryRaffleActivityByActivityId(Long activityId);

    /**
     * 通过活动 ID 检索关联的抽奖策略 ID
     * * 业务逻辑：
     * 活动（Activity）与策略（Strategy）通常为 1:1 关系。此查询用于在确定参与活动后，
     * 快速定位到具体的算法逻辑和奖品池配置。
     *
     * @param activityId 活动唯一标识
     * @return 关联的策略 ID (strategyId)
     */
    Long queryStrategyIdByActivityId(Long activityId);

    /**
     * 通过策略 ID 反查对应的活动 ID
     * * 典型场景：
     * 常用于运营后台数据回溯、或在执行完具体抽奖策略后，需要同步更新活动层级数据（如活动库存/参与人数）的场景。
     *
     * @param strategyId 策略唯一标识
     * @return 关联的活动 ID (activityId)
     */
    Long queryActivityIdByStrategyId(Long strategyId);

}