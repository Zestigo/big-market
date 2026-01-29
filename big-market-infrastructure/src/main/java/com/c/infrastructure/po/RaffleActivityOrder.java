package com.c.infrastructure.po;

import lombok.Data;

import java.util.Date;

/**
 * 抽奖活动订单持久化对象
 * 职责：
 * 记录用户参与抽奖活动的下单流水。当规则责任链校验通过后，
 * 系统会生成此订单，作为用户获得“抽奖机会”的凭证。
 *
 * @author cyh
 * @date 2026/01/25
 */
@Data
public class RaffleActivityOrder {

    /** 自增主键 ID */
    private Long id;

    /** 用户唯一标识（参与人） */
    private String userId;

    /** 商品 SKU 编号，关联具体的活动配置组合 */
    private Long sku;

    /** 活动唯一标识 */
    private Long activityId;

    /** 活动名称（冗余字段，减少关联查询，用于展示） */
    private String activityName;

    /** 关联的抽奖策略 ID（由活动配置决定具体转盘逻辑） */
    private Long strategyId;

    /** 业务订单 ID：全局唯一，用于标识单次抽奖行为 */
    private String orderId;

    /** 下单时间：记录用户发起参与请求的准确时刻 */
    private Date orderTime;

    /** 总次数限制（下单时的规则快照） */
    private Integer totalCount;

    /** 日次数限制（下单时的规则快照） */
    private Integer dayCount;

    /** 月次数限制（下单时的规则快照） */
    private Integer monthCount;

    /** 订单状态(not_used: 未使用, used: 已使用, expire: 已过期) */
    private String state;

    /** 业务幂等 ID：由外部透传（如：支付单号、积分扣减单号），确保同一业务操作不会重复发放抽奖机会 */
    private String outBusinessNo;

    /** 数据库记录创建时间 */
    private Date createTime;

    /** 数据库记录最后更新时间 */
    private Date updateTime;

}