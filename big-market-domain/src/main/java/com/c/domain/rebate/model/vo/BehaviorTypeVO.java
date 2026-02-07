package com.c.domain.rebate.model.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 行为类型值对象枚举
 *
 * @author cyh
 * @date 2026/02/05
 */
@Getter
@AllArgsConstructor
public enum BehaviorTypeVO {

    /** 签到返利行为 */
    SIGN("sign", "签到（日历）"),

    /** OpenAI 支付返利行为 */
    OPENAI_PAY("openai_pay", "openai 外部支付完成"),
    ;

    /** 行为类型编码 */
    private final String code;

    /** 行为类型描述 */
    private final String info;

}