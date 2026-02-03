package com.c.api;

import com.c.api.dto.ActivityDrawRequestDTO;
import com.c.api.dto.ActivityDrawResponseDTO;
import com.c.types.model.Response;

/**
 * 抽奖活动服务接口
 * 负责抽奖活动的初始化装配、预热以及用户抽奖行为的决策执行。
 *
 * @author cyh
 * @since 2026/02/02
 */
public interface IRaffleActivityService {

    /**
     * 活动数据装配（预热缓存）
     * 在活动启动前，将活动策略、奖品配置及概率矩阵预热到 Redis 缓存中，
     * 确保高并发环境下抽奖逻辑能够直接从缓存中读取数据，减少数据库压力。
     *
     * @param activityId 活动唯一标识 ID
     * @return Response<Boolean> 装配结果：true 表示装配成功，false 表示装配失败
     */
    Response<Boolean> armory(Long activityId);

    /**
     * 执行活动抽奖决策
     * 处理用户参与活动的抽奖请求。包含：校验活动状态、检查用户额度、
     * 执行抽奖算法、生成中奖记录及后续奖品发放流程。
     *
     * @param request 活动抽奖请求入参，包含用户 ID 和对应的活动 ID
     * @return Response<ActivityDrawResponseDTO> 抽奖中奖结果信息
     */
    Response<ActivityDrawResponseDTO> draw(ActivityDrawRequestDTO request);

}