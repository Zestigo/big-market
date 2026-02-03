package com.c.api;

import com.c.api.dto.RaffleAwardListRequestDTO;
import com.c.api.dto.RaffleAwardListResponseDTO;
import com.c.api.dto.RaffleStrategyRequestDTO;
import com.c.api.dto.RaffleStrategyResponseDTO;
import com.c.types.model.Response;

import java.util.List;

/**
 * 抽奖策略服务接口
 * 负责抽奖算法的初始化装配、奖品配置查询以及独立策略的随机抽奖决策。
 *
 * @author cyh
 * @since 2026/02/02
 */
public interface IRaffleStrategyService {

    /**
     * 策略数据装配
     * 针对具体的策略 ID 进行预热，计算概率区间并生成概率矩阵加载至缓存（如 Redis），
     * 以支持后续随机抽奖算法的快速响应。
     *
     * @param strategyId 抽奖策略唯一标识 ID
     * @return Response<Boolean> 装配结果：true 表示装配完成，false 表示装配失败
     */
    Response<Boolean> strategyArmory(Long strategyId);

    /**
     * 查询抽奖奖品列表
     * 根据策略 ID 查询该策略下关联的所有奖品信息，通常用于前端转盘、抽奖页面的奖品展示。
     *
     * @param requestDTO 奖品列表查询请求，包含策略 ID
     * @return Response<List<RaffleAwardListResponseDTO>> 奖品列表数据集合
     */
    Response<List<RaffleAwardListResponseDTO>> queryRaffleAwardList(RaffleAwardListRequestDTO requestDTO);

    /**
     * 独立随机抽奖（不含业务前置校验）
     * 纯粹执行策略层的随机算法决策。该接口仅负责根据概率矩阵计算中奖结果，
     * 不涉及用户额度、活动状态等业务校验，适用于内部测试或特定业务场景。
     *
     * @param requestDTO 抽奖请求，包含策略 ID
     * @return Response<RaffleStrategyResponseDTO> 随机抽出的奖品信息
     */
    Response<RaffleStrategyResponseDTO> randomRaffle(RaffleStrategyRequestDTO requestDTO);
}