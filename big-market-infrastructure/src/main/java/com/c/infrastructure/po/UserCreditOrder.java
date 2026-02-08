package com.c.infrastructure.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 用户积分流水订单持久化对象 (PO)
 * 职责：映射数据库表 `user_credit_order_00x`。
 * 记录每一笔积分变动的明细，作为资产对账和幂等校验的依据。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserCreditOrder {

    /** 自增主键 ID */
    private Long id;

    /** 用户 ID */
    private String userId;

    /** 业务单号（分布式唯一 ID，核心幂等键） */
    private String orderId;

    /** 交易名称（例如：抽奖返利、签到积分） */
    private String tradeName;

    /** 交易类型：forward-正向(加积分), reverse-反向(扣积分) */
    private String tradeType;

    /** 交易金额 (decimal(10,2)) */
    private BigDecimal amount;

    /** 创建时间 */
    private Date createTime;

    /** 更新时间 */
    private Date updateTime;

}