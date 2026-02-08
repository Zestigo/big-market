package com.c.domain.credit.model.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 交易类型枚举值对象
 * 职责：定义积分变动的正逆向维度，用于指导账户金额的加减逻辑。
 *
 * @author cyh
 * @date 2026/02/08
 */
@Getter
@AllArgsConstructor
public enum TradeTypeVO {

    FORWARD("forward", "正向交易，增加积分"),
    REVERSE("reverse", "逆向交易，扣减积分"),
    ;

    private final String code;
    private final String desc;

}