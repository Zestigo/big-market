package com.c.domain.award.model.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 交易名称值对象
 * 职责：定义具体的业务场景
 *
 * @author cyh
 * @date 2026/02/07
 */
@Getter
@AllArgsConstructor
public enum TradeNameVO {

    REBATE_REWARD("rebate_reward", "行为返利"),
    RAFFLE_CONSUME("raffle_consume", "抽奖消耗"),
    CANCEL_REVERSE("cancel_reverse", "冲正/撤销"),
    CONVERT_SKU("convert_sku", "兑换商品");

    private final String code;
    private final String info;

}