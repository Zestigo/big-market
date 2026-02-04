package com.c.domain.strategy.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 策略奖品实体
 * 描述特定抽奖策略下各奖品的配置信息、库存状态及中奖概率
 *
 * @author cyh
 * @since 2026/02/02
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StrategyAwardEntity {

    /** 策略 ID */
    private Long strategyId;

    /** 奖品 ID */
    private Integer awardId;

    /** 奖品标题 */
    private String awardTitle;

    /** 奖品副标题 */
    private String awardSubtitle;

    /** 奖品库存总量 */
    private Integer awardCount;

    /** 奖品剩余库存 */
    private Integer awardCountSurplus;

    /** 中奖概率 (例如：0.005 表示 0.5%) */
    private BigDecimal awardRate;

    /** 排序编号 */
    private Integer sort;

    /** 规则模型 (用于存储抽奖过程中的各类过滤规则，如：rule_luck_award, rule_lock 等) */
    private String ruleModels;

}