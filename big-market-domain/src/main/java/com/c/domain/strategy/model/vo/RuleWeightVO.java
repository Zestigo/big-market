package com.c.domain.strategy.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 抽奖权重规则配置值对象
 *
 * @author cyh
 * @date 2026/02/07
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RuleWeightVO {

    /** 原始规则配置值 (示例: 4000:101,102) */
    private String ruleValue;

    /** 解析后的权重阈值 (对应触发次数) */
    private Integer weight;

    /** 当前档位关联的奖品 ID 集合 (用于计算) */
    private List<Integer> awardIds;

    /** 当前档位关联的奖品明细列表 (用于展示) */
    private List<Award> awardList;

    @Getter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Award {
        /** 奖品 ID */
        private Integer awardId;
        /** 奖品标题 */
        private String awardTitle;
    }

}