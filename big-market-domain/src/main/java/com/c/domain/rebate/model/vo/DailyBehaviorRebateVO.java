package com.c.domain.rebate.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 每日行为返利配置视图对象
 *
 * @author cyh
 * @date 2026/02/05
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DailyBehaviorRebateVO {

    /** 行为类型标识（sign 签到、openai_pay 支付） */
    private String behaviorType;

    /** 返利业务描述 */
    private String rebateDesc;

    /** 返利奖励类型（sku 活动商品、integral 用户积分） */
    private String rebateType;

    /** 返利具体配置（根据类型存储对应的商品ID或积分值） */
    private String rebateConfig;

}