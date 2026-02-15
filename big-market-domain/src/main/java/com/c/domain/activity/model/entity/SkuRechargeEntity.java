package com.c.domain.activity.model.entity;

import com.c.domain.activity.model.vo.OrderTradeTypeVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 活动参与充值实体（SKU 充值意图对象）
 * * @author cyh
 * @date 2026/01/27
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SkuRechargeEntity {

    /** 用户唯一标识 ID */
    private String userId;

    /** 活动 SKU 编码 */
    private Long sku;

    /** 外部业务单号（幂等单号） */
    private String outBusinessNo;

    /** 交易类型枚举（默认：返利无支付交易） */
    @Builder.Default
    private OrderTradeTypeVO orderTradeType = OrderTradeTypeVO.REBATE_NO_PAY_TRADE;

}