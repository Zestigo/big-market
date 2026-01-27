package com.c.domain.activity.model.aggregate;

import com.c.domain.activity.model.entity.ActivityAccountEntity;
import com.c.domain.activity.model.entity.ActivityOrderEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author cyh
 * @description 创建订单聚合对象
 * 1. 聚合职责：该聚合将“抽奖活动订单”与“用户活动账户”包装在一起，确保在创建订单的同时，能够同步更新或校验账户状态。
 * 2. 事务一致性：在领域服务或仓储实现中，该聚合通常作为一个整体进行持久化，保证订单的产生和账户余额（次数）的扣减处于同一个事务中。
 * @date 2026/01/27
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CreateOrderAggregate {

    /**
     * 用户抽奖活动账户实体
     * 包含用户在该活动下的总剩余次数、日剩余次数、月剩余次数等。
     * 在创建订单时，需要通过该实体校验用户是否有足够的参与资格，并进行次数扣减。
     */
    private ActivityAccountEntity activityAccountEntity;

    /**
     * 活动抽奖订单实体
     * 记录本次参与活动的流水信息，包括订单 ID、用户 ID、SKU 信息、活动 ID 以及订单状态等。
     * 是用户参与抽奖资格的凭证。
     */
    private ActivityOrderEntity activityOrderEntity;

}