package com.c.infrastructure.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
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
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RaffleActivitySku {

    /** 数据库自增主键ID */
    private Long id;

    /** 活动SKU业务编码，对外暴露的唯一参与标识 */
    private Long sku;

    /** 抽奖活动业务ID */
    private Long activityId;

    /** 次数限制模板业务ID */
    private Long activityCountId;

    /** 活动总库存次数 */
    private Integer stockCount;

    /** 活动剩余库存次数 */
    private Integer stockCountSurplus;

    /** 商品金额【积分】 - 对应 DDL 中的 product_amount */
    private BigDecimal productAmount;

    /** 状态（0-有效、1-无效） - 对应 DDL 中的 state */
    private Integer state;

    /** 记录创建时间 */
    private Date createTime;

    /** 记录最后更新时间 */
    private Date updateTime;

}