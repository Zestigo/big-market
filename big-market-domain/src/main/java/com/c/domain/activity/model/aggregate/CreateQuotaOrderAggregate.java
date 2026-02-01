package com.c.domain.activity.model.aggregate;

import com.c.domain.activity.model.entity.ActivityOrderEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author cyh
 * @description 创建活动参与订单聚合对象
 * 1. 职责定义：将“活动流水订单”与“账户额度变更”封装为原子操作。在用户通过 SKU 充值或领取资格时，
 * 该聚合对象承载了产生新订单以及对应账户次数增加的全部上下文。
 * 2. 事务一致性：仓储层在处理此聚合时，应在同一个数据库事务内完成 ActivityOrder 表的插入和 ActivityAccount 表的额度更新。
 * @date 2026/01/27
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CreateQuotaOrderAggregate {

    /**
     * 用户唯一标识
     */
    private String userId;

    /**
     * 活动唯一标识
     */
    private Long activityId;

    /**
     * 本次订单应增加的总次数限制
     * 对应 ActivityCountEntity 中的 totalCount，由 SKU 配置决定。
     */
    private Integer totalCount;

    /**
     * 本次订单应增加的日次数限制
     * 对应 ActivityCountEntity 中的 dayCount，控制用户当日参与上限。
     */
    private Integer dayCount;

    /**
     * 本次订单应增加的月次数限制
     * 对应 ActivityCountEntity 中的 monthCount，控制用户单月参与上限。
     */
    private Integer monthCount;

    /**
     * 活动订单流水实体
     * 记录本次充值/领取的详细流水，包括单号、SKU 信息、状态等。
     */
    private ActivityOrderEntity activityOrderEntity;

}