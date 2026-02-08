package com.c.domain.credit.model.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 交易名称枚举值对象
 * 职责：定义积分流水的业务来源名称
 *
 * @author cyh
 * @date 2026/02/08
 */
@Getter
@AllArgsConstructor
public enum TradeNameVO {

    REBATE("REBATE", "行为返利"),
    CONVERT_SKU("CONVERT_SKU", "兑换抽奖"),
    CREDIT_REDEEM("CREDIT_REDEEM", "积分兑换"),
    ;

    private final String code;
    private final String desc;

}