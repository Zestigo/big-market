package com.c.api;

import com.c.api.dto.*;
import com.c.types.model.Response;

import java.util.List;

/**
 * 抽奖策略服务接口
 * 核心职责：负责策略数据装配（预热）、奖品信息检索、以及基于概率矩阵的独立抽奖决策。
 *
 * @author cyh
 * @since 2026/02/02
 */
public interface IRaffleStrategyService {

    /**
     * 策略数据装配（策略武器库初始化）
     * 对特定策略进行概率建模，将奖品概率分布转化为内存/缓存矩阵，为高并发抽奖提供 $O(1)$ 时间复杂度的查找能力。
     *
     * @param strategyId 策略唯一标识
     * @return Response<Boolean> 装配成功返回 true
     */
    Response<Boolean> strategyArmory(Long strategyId);

    /**
     * 查询抽奖策略配置的奖品列表
     * 检索指定策略下所有可展示的奖品元数据，用于前端抽奖转盘、宫格或列表页的渲染。
     *
     * @param requestDTO 包含 strategyId 的请求对象
     * @return Response<List < RaffleAwardListResponseDTO>> 奖品展示数据列表
     */
    Response<List<RaffleAwardListResponseDTO>> queryRaffleAwardList(RaffleAwardListRequestDTO requestDTO);

    /**
     * 执行独立随机抽奖（不触发业务规则链）
     * 仅根据策略配置的概率进行随机决策，不校验活动时间、库存、用户额度等外部限制。
     * 常用于系统测试、简单营销逻辑触发或作为大抽奖流程中的原子决策节点。
     *
     * @param requestDTO 包含 strategyId 的抽奖请求
     * @return Response<RaffleStrategyResponseDTO> 随机命中的奖品结果
     */
    Response<RaffleStrategyResponseDTO> randomRaffle(RaffleStrategyRequestDTO requestDTO);

    /**
     * 查询权重规则配置及用户进度
     * 用于展示“阶梯式”抽奖福利（如：累计抽满 N 次必进高级奖池）。
     * 该接口会返回该策略下所有的权重档位及对应的奖品范围，并包含用户当前的实际抽奖进度。
     *
     * @param request 包含 userId 和 activityId 的请求对象
     * @return Response<List < RaffleStrategyRuleWeightResponseDTO>> 权重档位列表。
     * 说明：前端可据此渲染“进度条”或“进阶奖池”列表，根据已达标档位引导用户继续参与。
     */
    Response<List<RaffleStrategyRuleWeightResponseDTO>> queryRaffleStrategyRuleWeight(RaffleStrategyRuleWeightRequestDTO request);

}