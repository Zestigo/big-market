package com.c.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 抽奖策略规则权重查询响应 DTO
 * 描述：返回用户当前活动下的抽奖进度及对应权重等级可抽取的奖品列表
 *
 * @author cyh
 * @date 2026/02/07
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RaffleStrategyRuleWeightResponseDTO implements Serializable {

    /** 解锁当前权重等级所需的总抽奖次数（阈值） */
    private Integer ruleWeightCount;

    /** 用户在该活动下累计已消耗的抽奖次数（实际进度） */
    private Integer userActivityTotalCount;

    /** 当前权重等级涵盖的奖品范围 */
    private List<StrategyAward> strategyAwards;

    /** 奖品简略信息实体 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StrategyAward {
        /** 奖品ID */
        private Integer awardId;

        /** 奖品名称/标题 */
        private String awardTitle;
    }

}