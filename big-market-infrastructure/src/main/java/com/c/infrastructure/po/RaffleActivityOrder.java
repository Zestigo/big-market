package com.c.infrastructure.po;

import lombok.Data;

import java.util.Date;

/**
 * 抽奖活动订单持久化对象
 * 职责：记录用户参与活动的下单情况，是用户发起抽奖请求后的第一道持久化凭证。
 *
 * @author cyh
 * @date 2026/01/25
 */
@Data
public class RaffleActivityOrder {

    /** 自增ID */
    private Long id;

    /** 用户ID */
    private String userId;

    /** 活动ID */
    private Long activityId;

    /** 活动名称 */
    private String activityName;

    /** 抽奖策略ID */
    private Long strategyId;

    /** 订单ID（业务唯一标识，用于防重和后续奖品发放关联） */
    private String orderId;

    /** 下单时间（用户发起参与的时间） */
    private Date orderTime;

    /** 订单状态（not_used-未使用、used-已使用、expire-已过期） */
    private String state;

    /** 创建时间 */
    private Date createTime;

    /** 更新时间 */
    private Date updateTime;

}