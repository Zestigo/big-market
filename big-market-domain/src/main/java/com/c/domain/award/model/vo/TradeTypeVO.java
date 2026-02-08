package com.c.domain.award.model.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 交易类型值对象
 * 职责：定义积分的流向属性
 *
 * @author cyh
 * @date 2026/02/07
 */
@Getter
@AllArgsConstructor
public enum TradeTypeVO {

    FORWARD("forward", "正向，增加积分"),
    REVERSE("reverse", "反向，扣减积分");

    private final String code;
    private final String info;

}