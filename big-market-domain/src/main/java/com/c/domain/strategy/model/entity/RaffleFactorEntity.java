package com.c.domain.strategy.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author cyh
 * @description 抽奖因子实体：封装本次抽奖执行所需的关键触发参数
 * @date 2026/02/02
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RaffleFactorEntity {

    /** 参与抽奖的用户唯一标识 */
    private String userId;

    /** 本次抽奖所关联的策略模型 ID */
    private Long strategyId;

}