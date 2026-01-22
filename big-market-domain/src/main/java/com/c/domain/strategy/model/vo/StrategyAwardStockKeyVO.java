package com.c.domain.strategy.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 策略奖品库存key标识对象
 *
 * @author cyh
 * @date 2026/01/20
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StrategyAwardStockKeyVO {
    private Long strategyId;
    private Integer awardId;
}
