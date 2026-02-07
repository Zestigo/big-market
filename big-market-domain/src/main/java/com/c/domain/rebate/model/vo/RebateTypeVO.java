package com.c.domain.rebate.model.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 返利类型枚举值对象
 *
 * @author cyh
 * @date 2026/02/05
 */
@Getter
@AllArgsConstructor
public enum RebateTypeVO {

    /** 活动库存充值商品（如：抽奖次数） */
    SKU("sku", "活动库存充值商品"),

    /** 用户活动积分（如：账户可用分值） */
    INTEGRAL("integral", "用户活动积分"),
    ;

    /** 类型编码 */
    private final String code;
    /** 类型描述 */
    private final String info;

}