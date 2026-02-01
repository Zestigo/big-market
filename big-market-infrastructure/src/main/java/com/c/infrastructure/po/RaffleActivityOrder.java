package com.c.infrastructure.po;

import lombok.Data;
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
public class RaffleActivityOrder {

    /** 数据库自增主键ID */
    private Long id;

    /** 用户唯一标识，记录抽奖参与人 */
    private String userId;

    /** 活动SKU业务编码，外键关联RaffleActivitySKU.sku，绑定参与的具体活动单元 */
    private Long sku;

    /** 抽奖活动业务ID，外键关联raffle_activity表，冗余字段减少联表查询 */
    private Long activityId;

    /** 活动名称，冗余字段：直接展示/对账使用，无需关联活动表查询 */
    private String activityName;

    /** 抽奖策略业务ID，关联具体的抽奖转盘/奖项规则逻辑 */
    private Long strategyId;

    /** 业务订单ID：全局唯一，标识用户单次抽奖行为，用于订单追溯/查询 */
    private String orderId;

    /** 下单时间：用户发起参与抽奖请求的**实际时间**，作为次数统计（日/月）的时间依据 */
    private Date orderTime;

    /** 总次数限制：**下单时的规则快照**，留存参与时的总次数限制，不受后续模板修改影响 */
    private Integer totalCount;

    /** 日次数限制：**下单时的规则快照**，留存参与时的日次数限制，不受后续模板修改影响 */
    private Integer dayCount;

    /** 月次数限制：**下单时的规则快照**，留存参与时的月次数限制，不受后续模板修改影响 */
    private Integer monthCount;

    /** 订单状态：not_used(未使用)、used(已使用)、expire(已过期)，标识抽奖机会的使用状态 */
    private String state;

    /** 业务幂等ID：由外部透传（如支付单号/积分扣减单号），防止同一业务操作重复发放抽奖机会 */
    private String outBusinessNo;

    /** 记录创建时间 */
    private Date createTime;

    /** 记录最后更新时间（用于状态变更/过期处理追溯） */
    private Date updateTime;

}