package com.c.domain.activity.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 未支付活动订单实体
 *
 * @author cyh
 * @date 2026/02/16
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UnpaidActivityOrderEntity {

    /* 用户唯一标识 ID */
    private String userId;

    /* 内部业务订单唯一标识 ID */
    private String orderId;

    /* 外部业务透传 ID (如支付流水号或来源单号) */
    private String outBusinessNo;

    /* 订单待支付金额 */
    private BigDecimal payAmount;

}