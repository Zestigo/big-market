package com.c.domain.strategy.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * @author cyh
 * @description 策略奖品实体：记录特定抽奖策略下各奖品的配置、库存及概率
 * @date 2026/02/02
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StrategyAwardEntity {

    /** 策略ID */
    private Long strategyId;

    /** 奖品ID（关联自奖品配置表） */
    private Integer awardId;

    /** 奖品标题（如：华为 Mate 70 Pro） */
    private String awardTitle;

    /** 奖品副标题（如：颜色随机，抽中后联系客服领取） */
    private String awardSubtitle;

    /** 奖品总数量（该策略下允许发出的最大数量） */
    private Integer awardCount;

    /** 奖品剩余库存（动态扣减后的数量） */
    private Integer awardCountSurplus;

    /**
     * 中奖概率
     * 使用 BigDecimal 以保证浮点数运算精度，如 0.05 代表 5%
     */
    private BigDecimal awardRate;

    /** 排序：定义奖品在页面展现或逻辑计算中的优先级 */
    private Integer sort;

}