package com.c.domain.credit.model.entity;

import com.c.domain.credit.model.vo.TradeNameVO;
import com.c.domain.credit.model.vo.TradeTypeVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 积分流水订单实体
 * 职责：定义一笔积分变动的完整交易信息，用于生成记账流水和驱动账户变更。
 *
 * @author cyh
 * @date 2026/02/08
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CreditOrderEntity {

    /** 用户ID */
    private String userId;

    /** 内部订单号：本系统生成的全局唯一标识，用于对账 */
    private String orderId;

    /** 交易名称：如返利、兑换等业务描述 */
    private TradeNameVO tradeName;

    /** 交易类型：forward-正向加积分、reverse-逆向扣积分 */
    private TradeTypeVO tradeType;

    /** 交易金额：涉及账户变动的数值 */
    private BigDecimal tradeAmount;

    /** 外部业务防重单号：由上游透传，是全链路幂等校验的核心键 */
    private String outBusinessNo;

}