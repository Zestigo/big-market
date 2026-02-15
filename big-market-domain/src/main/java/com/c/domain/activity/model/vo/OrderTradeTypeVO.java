package com.c.domain.activity.model.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 订单交易类型枚举
 * 核心职责：区分用户参与活动的成本属性，决定后续是否需要调用积分扣减接口
 *
 * @author cyh
 * @date 2026/02/09
 */
@Getter
@AllArgsConstructor
public enum OrderTradeTypeVO {

    CREDIT_PAY_TRADE("credit_pay_trade", "积分支付交易：用户需消耗积分兑换抽奖机会"),
    REBATE_NO_PAY_TRADE("rebate_no_pay_trade", "返利无支付交易：由行为返利直接赠送，不扣减积分"),
    ;

    private final String code;
    private final String desc;

    /**
     * 根据 code 获取枚举对象
     * 场景：用于从数据库读取状态或从 MQ/前端接收字符串后，快速转换为枚举
     */
    public static OrderTradeTypeVO fromCode(String code) {
        return Arrays
                .stream(OrderTradeTypeVO.values())
                .filter(type -> type
                        .getCode()
                        .equalsIgnoreCase(code))
                .findFirst()
                .orElse(null);
    }

}