package com.c.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 抽奖策略查询响应对象
 *
 * @author cyh
 * @since 2026/02/02
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RaffleStrategyResponseDTO implements Serializable {

    /** 奖品唯一标识 ID */
    private Integer awardId;

    /** 奖品排序索引 (对应抽奖策略配置的顺序编号) */
    private Integer awardIndex;

}