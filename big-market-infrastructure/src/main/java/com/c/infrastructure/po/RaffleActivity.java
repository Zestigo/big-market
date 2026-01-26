package com.c.infrastructure.po;

import lombok.Data;

import java.util.Date;

/**
 * 抽奖活动持久化对象
 * 职责：定义活动的元数据信息，包括有效期、关联的抽奖策略以及活动维度的总库存。
 *
 * @author cyh
 * @date 2026/01/25
 */
@Data
public class RaffleActivity {

    /** 自增ID */
    private Long id;

    /** 活动ID（对外业务标识，全局唯一） */
    private Long activityId;

    /** 活动名称 */
    private String activityName;

    /** 活动描述 */
    private String activityDesc;

    /** 活动开始时间 */
    private Date beginDateTime;

    /** 活动结束时间 */
    private Date endDateTime;

    /** 活动总库存（决定了该活动最多发出的总抽奖次数） */
    private Integer stockCount;

    /** 剩余库存（实时扣减，用于活动维度的超卖控制） */
    private Integer stockCountSurplus;

    /** 活动参与次数配置ID（关联 RaffleActivityCount 表） */
    private Long activityCountId;

    /** 抽奖策略ID（关联 Strategy 表，定义具体怎么抽奖） */
    private Long strategyId;

    /** 活动状态（open-开启、close-关闭、wait_open-待开启） */
    private String state;

    /** 创建时间 */
    private Date createTime;

    /** 更新时间 */
    private Date updateTime;

}