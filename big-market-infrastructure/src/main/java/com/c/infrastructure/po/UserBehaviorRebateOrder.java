package com.c.infrastructure.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 用户行为返利流水记录
 * 关联数据库表：user_behavior_rebate_order
 * 业务描述：记录用户触发行为后，系统执行返利发放的具体订单流水，用于幂等校验与账目追溯。
 *
 * @author cyh
 * @since 2026/02/05
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserBehaviorRebateOrder {

    /** 自增主键 */
    private Long id;

    /** 用户ID */
    private String userId;

    /** 系统内部订单ID（全局唯一） */
    private String orderId;

    /** 行为类型（sign-签到、openai_pay-支付） */
    private String behaviorType;

    /** 返利内容描述 */
    private String rebateDesc;

    /** 返利分发类型（sku-活动额度、integral-积分） */
    private String rebateType;

    /** 返利配置内容（SKU值或积分额度） */
    private String rebateConfig;

    /** 外部业务单号（对应外部事件的唯一标识） */
    private String outBusinessNo;

    /** 业务防重幂等ID（基于业务规则生成的唯一索引键） */
    private String bizId;

    /** 创建时间 */
    private Date createTime;

    /** 更新时间 */
    private Date updateTime;

}