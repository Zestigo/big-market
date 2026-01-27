package com.c.infrastructure.po;

import lombok.Data;

import java.util.Date;

/**
 * @description 抽奖活动 SKU 持久化对象（Persistent Object）
 * 1. 对应数据库表：raffle_activity_sku
 * 2. 职责：SKU 是活动领取的最小颗粒度单元，封装了活动、次数配置与库存的关联关系。
 * * @author cyh
 * @date 2026/01/27
 */
@Data
public class RaffleActivitySKU {

    /**
     * 自增主键 ID
     */
    private Long id;

    /**
     * 商品库存单位编码（SKU）
     * 对外暴露的业务标识，用户通过此编码参与对应的抽奖活动。
     */
    private Long sku;

    /**
     * 抽奖活动 ID
     * 对应 raffle_activity 表的业务标识，用于关联具体的活动配置（如活动名称、时间、状态）。
     */
    private Long activityId;

    /**
     * 活动次数配置编号
     * 对应 raffle_activity_count 表的标识，用于定义该 SKU 参与时的次数限制（总、日、月次数）。
     */
    private Long activityCountId;

    /**
     * 活动总库存次数
     * 该 SKU 允许发放的总参与资格数量（例如：该活动总共只能被领取 1000 次）。
     */
    private Integer stockCount;

    /**
     * 剩余库存次数
     * 实时记录当前还剩多少可领取的资格，用于并发扣减库存。
     */
    private Integer stockCountSurplus;

    /**
     * 创建时间
     * 记录记录首次插入数据库的时间。
     */
    private Date createTime;

    /**
     * 更新时间
     * 记录数据最后一次修改的时间，常用于乐观锁或数据同步。
     */
    private Date updateTime;

}