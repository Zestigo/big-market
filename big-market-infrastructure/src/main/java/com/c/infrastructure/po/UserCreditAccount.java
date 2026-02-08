package com.c.infrastructure.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 用户积分账户持久化对象 (PO)
 * 职责：对应数据库表 `user_credit_account`。记录用户积分资产的实时状态，
 * 承载总额度（累计获得）与可用额度（当前余额）的核心数据映射。
 *
 * @author cyh
 * @date 2026/02/07
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserCreditAccount {

    /** 自增主键 ID */
    private Long id;

    /** 用户唯一标识 */
    private String userId;

    /** 总积分（累计积分）：记录用户历史上所有获得积分的总和，原则上该字段仅增不减。 */
    private BigDecimal totalAmount;

    /** 可用积分（账户余额） 当前账户下实时可消费、扣减的积分水位。 */
    private BigDecimal availableAmount;

    /** 账户状态枚举值：open (可用), close (冻结) */
    private String accountStatus;

    /** 创建时间 */
    private Date createTime;

    /** 更新时间（自动触发 ON UPDATE CURRENT_TIMESTAMP） */
    private Date updateTime;

}