package com.c.infrastructure.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 抽奖活动-参与订单持久化对象
 * 核心职责：用户参与抽奖活动的**流水记录+抽奖凭证**，规则校验通过后生成，记录单次参与的全量关键信息
 * 关键设计：冗余/快照字段用于减少联表查询、保证数据可追溯，不受上游配置修改影响
 *
 * @author cyh
 * @date 2026/01/25
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RaffleActivityOrder {

    /** 数据库自增主键ID */
    private Long id;

    /** 用户唯一标识 */
    private String userId;

    /** 活动SKU业务编码 */
    private Long sku;

    /** 抽奖活动业务ID */
    private Long activityId;

    /** 活动名称（冗余快照） */
    private String activityName;

    /** 抽奖策略业务ID */
    private Long strategyId;

    /** 业务订单ID（分布式唯一ID） */
    private String orderId;

    /** 下单时间 */
    private Date orderTime;

    /** 总次数限制（下单快照） */
    private Integer totalCount;

    /** 日次数限制（下单快照） */
    private Integer dayCount;

    /** 月次数限制（下单快照） */
    private Integer monthCount;

    /** 支付金额【积分】 - 对应 DDL 中的 pay_amount */
    private BigDecimal payAmount;

    /** 订单状态：complete(已完成)、wait_pay(待支付) 等 */
    private String state;

    /** 业务幂等ID：由外部透传，防止重复发放抽奖机会 */
    private String outBusinessNo;

    /** 记录创建时间 */
    private Date createTime;

    /** 记录最后更新时间 */
    private Date updateTime;

}