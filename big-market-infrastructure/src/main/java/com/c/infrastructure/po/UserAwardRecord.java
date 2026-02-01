package com.c.infrastructure.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 用户中奖记录持久化对象
 * 职责：记录用户中奖的详细信息，作为后续发奖、核销以及中奖名单展示的数据源。
 *
 * @author cyh
 * @date 2026/02/01
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserAwardRecord {

    /** 自增ID */
    private Long id;

    /** 用户唯一标识 */
    private String userId;

    /** 活动标识 */
    private Long activityId;

    /** 抽奖策略标识 */
    private Long strategyId;

    /** 关联的抽奖订单ID（核心幂等字段：确保一笔抽奖订单有且仅有一条中奖记录） */
    private String orderId;

    /** 中奖奖品标识 */
    private Integer awardId;

    /** 奖品标题（记录中奖瞬间的奖品名称） */
    private String awardTitle;

    /** 中奖时间 */
    private Date awardTime;

    /** 奖品发放状态；create-已创建（待发奖）、completed-已完成（发奖成功） */
    private String awardState;

    /** 创建时间 */
    private Date createTime;

    /** 更新时间 */
    private Date updateTime;

}