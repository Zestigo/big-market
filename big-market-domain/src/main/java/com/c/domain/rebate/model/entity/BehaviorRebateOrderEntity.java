package com.c.domain.rebate.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 行为返利订单流水实体
 *
 * @author cyh
 * @date 2026/02/05
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BehaviorRebateOrderEntity {

    /** 用户唯一ID */
    private String userId;

    /** 业务单据号（系统生成的唯一返利订单号） */
    private String orderId;

    /** 行为类型（sign-签到、openai_pay-支付） */
    private String behaviorType;

    /** 返利业务描述（展示给用户的记录名称） */
    private String rebateDesc;

    /** 返利分发类型（sku-活动额度、integral-积分奖励） */
    private String rebateType;

    /** 返利配置参数（SKU编码或具体的积分值） */
    private String rebateConfig;

    /** 外部业务单号（如：签到日期、外部交易流水号） */
    private String outBusinessNo;

    /** 业务幂等标识（用于防止重复发放奖励的唯一约束键） */
    private String bizId;

}