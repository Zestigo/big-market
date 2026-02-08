package com.c.infrastructure.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 用户积分流水持久化对象
 * 对应表：user_credit_order_00x (分片表)
 *
 * @author cyh
 * @date 2026/02/08
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserCreditOrder {

    /** 自增主键 */
    private Long id;

    /** 用户 ID (分片键) */
    private String userId;

    /** 内部交易单号 (唯一索引: uq_order_id) */
    private String orderId;

    /** 交易名称 (示例: 抽奖返利、签到积分) */
    private String tradeName;

    /** 交易类型：forward-加积分, reverse-扣积分 */
    private String tradeType;

    /** 交易金额 */
    private BigDecimal tradeAmount;

    /** 外部业务单号 (唯一索引: uq_out_business_no，用于防重校验) */
    private String outBusinessNo;

    /** 创建时间 */
    private Date createTime;

    /** 更新时间 */
    private Date updateTime;

}