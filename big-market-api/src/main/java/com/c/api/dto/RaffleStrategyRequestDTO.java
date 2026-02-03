package com.c.api.dto;

import lombok.Data;
import java.io.Serializable;

/**
 * 抽奖策略查询请求对象
 *
 * @author cyh
 * @since 2026/02/02
 */
@Data
public class RaffleStrategyRequestDTO implements Serializable {

    /** 抽奖策略 ID (必填) */
    private Long strategyId;

}