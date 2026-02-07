package com.c.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 抽奖奖品列表查询请求对象
 *
 * @author cyh
 * @since 2026/02/02
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RaffleAwardListRequestDTO implements Serializable {

    /**
     * 抽奖策略 ID
     * @deprecated 该字段已废弃，建议通过活动 ID 进行关联查询
     */
    @Deprecated
    private Long strategyId;

    /** 活动 ID (用于定位当前抽奖活动) */
    private Long activityId;

    /** 用户 ID (必填，用于查询该用户对应的奖品解锁状态) */
    private String userId;
}