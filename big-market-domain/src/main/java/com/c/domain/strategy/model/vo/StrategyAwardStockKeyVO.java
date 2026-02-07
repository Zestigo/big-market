package com.c.domain.strategy.model.vo;

import com.c.types.common.Constants;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 值对象：策略奖品库存 Redis Key 标识
 * 职责：作为缓存层（Redis）中奖品库存扣减的唯一索引标识。
 * 作用：统一策略 ID 与奖品 ID 的组合规则，确保库存操作的原子性与隔离性。
 *
 * @author cyh
 * @date 2026/01/20
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StrategyAwardStockKeyVO {

    /** 策略 ID：归属的抽奖策略 */
    private Long strategyId;

    /** 奖品 ID：具体的奖品项 */
    private Integer awardId;
}