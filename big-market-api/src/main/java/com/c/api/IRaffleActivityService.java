package com.c.api;

import com.c.api.dto.ActivityDrawRequestDTO;
import com.c.api.dto.ActivityDrawResponseDTO;
import com.c.api.dto.UserActivityAccountRequestDTO;
import com.c.api.dto.UserActivityAccountResponseDTO;
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
     * 在活动启动前，将活动策略、奖品配置及概率矩阵预热到 Redis 缓存中。
     *
     * @param activityId 活动唯一标识 ID
     * @return Response<Boolean> 装配结果：true 表示成功
     */
    Response<Boolean> armory(Long activityId);

    /**
     * 执行活动抽奖决策
     * 处理用户参与活动的抽奖请求，包含校验活动状态、检查额度、计算抽奖结果。
     *
     * @param request 活动抽奖请求入参，包含用户 ID 和对应的活动 ID
     * @return Response<ActivityDrawResponseDTO> 抽奖中奖结果信息
     */
    Response<ActivityDrawResponseDTO> draw(ActivityDrawRequestDTO request);

    /**
     * 日历签到返利接口
     * 用户通过每日签到行为触发返利奖励，入账对应的活动额度或积分。
     *
     * @param userId 用户ID
     * @return Response<Boolean> 签到结果：true 签到成功
     */
    Response<Boolean> calendarSignRebate(String userId);

    /**
     * 判断是否完成日历签到
     * 查询用户当日是否已经领取过签到返利奖励，用于前端展示置灰或状态控制。
     *
     * @param userId 用户ID
     * @return Response<Boolean> true 已签到，false 未签到
     */
    Response<Boolean> isCalendarSignRebate(String userId);

    /**
     * 查询用户活动账户额度
     * 获取用户在当前活动下的实时额度信息，包括总次数、日次数、月次数的剩余情况。
     *
     * @param request 请求对象（活动ID、用户ID）
     * @return Response<UserActivityAccountResponseDTO> 账户额度详情
     */
    Response<UserActivityAccountResponseDTO> queryUserActivityAccount(UserActivityAccountRequestDTO request);

}