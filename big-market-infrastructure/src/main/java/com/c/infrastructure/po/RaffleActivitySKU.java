package com.c.infrastructure.po;

import lombok.Data;
import java.util.Date;

/**
 * 抽奖活动-SKU持久化对象（活动领取最小颗粒度单元）
 * 对应数据库表：raffle_activity_sku
 * 核心职责：封装「活动配置+次数限制模板+活动库存」的关联关系，是用户参与活动的唯一入口载体
 * 关键设计：库存字段为所有用户共享，次数限制通过关联模板指向单用户规则
 *
 * @author cyh
 * @date 2026/01/27
 */
@Data
public class RaffleActivitySKU {

    /** 数据库自增主键ID */
    private Long id;

    /** 活动SKU业务编码，对外暴露的唯一参与标识，用户通过此编码参与对应抽奖活动 */
    private Long sku;

    /** 抽奖活动业务ID，外键关联raffle_activity表，绑定具体活动（名称/时间/状态等） */
    private Long activityId;

    /** 次数限制模板业务ID，外键关联RaffleActivityCount.activityCountId，复用次数限制规则 */
    private Long activityCountId;

    /** 活动总库存次数：该SKU可发放的**全局总参与资格数**（所有用户共享），配置后一般不修改 */
    private Integer stockCount;

    /** 活动剩余库存次数：实时记录可领取的参与资格数，支持并发扣减，扣至0则活动停止领取 */
    private Integer stockCountSurplus;

    /** 记录创建时间 */
    private Date createTime;

    /** 记录最后更新时间（用于乐观锁/数据同步/库存变更追溯） */
    private Date updateTime;

}