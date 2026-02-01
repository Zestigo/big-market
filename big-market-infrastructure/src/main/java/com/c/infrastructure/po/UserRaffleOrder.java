package com.c.infrastructure.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 用户抽奖订单持久化对象
 * 职责：记录用户参与抽奖活动的下单凭证，是参与活动状态流转的核心单据。
 *
 * @author cyh
 * @date 2026/02/01
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserRaffleOrder {

    /** 自增ID / 主键 */
    private Long id;

    /** 用户唯一标识 */
    private String userId;

    /** 活动唯一标识 */
    private Long activityId;

    /** 活动名称（冗余存储，减少联查） */
    private String activityName;

    /** 抽奖策略ID */
    private Long strategyId;

    /** 外部业务订单ID（全局唯一标识，用于幂等校验） */
    private String orderId;

    /** 下单时间 */
    private Date orderTime;

    /** 订单状态；create-已创建、used-已使用（已完成抽奖）、cancel-已作废 */
    private String orderState;

    /** 创建时间 */
    private Date createTime;

    /** 更新时间 */
    private Date updateTime;

}