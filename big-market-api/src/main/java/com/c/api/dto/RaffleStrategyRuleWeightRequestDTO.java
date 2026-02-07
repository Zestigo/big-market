package com.c.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 抽奖策略规则权重请求 DTO
 * 用于查询用户在特定活动下的权重档位信息（如：累计抽奖次数达标状态）
 *
 * @author cyh
 * @date 2026/02/07
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RaffleStrategyRuleWeightRequestDTO {

    /** 用户唯一标识 */
    private String userId;

    /** 抽奖活动ID */
    private Long activityId;

}