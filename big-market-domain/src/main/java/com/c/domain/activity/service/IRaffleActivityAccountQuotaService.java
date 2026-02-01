package com.c.domain.activity.service;

import com.c.domain.activity.model.entity.SkuRechargeEntity;

/**
 * @author cyh
 * @description 抽奖活动订单服务接口
 * @date 2026/01/27
 */
public interface IRaffleActivityAccountQuotaService {
    /**
     * 以 sku 创建抽奖活动订单。
     * 该方法是用户参与活动的入口，通过校验 sku、活动状态及次数限制，最终生成参与活动所需的订单（消耗包/参与资格）。
     *
     * @param skuRechargeEntity 抽奖购物车实体对象，包含用户 ID、SKU 信息等。
     * @return ActivityOrderEntity 活动参与订单实体，包含订单 ID、状态及后续抽奖所需的凭证。
     */
    String createOrder(SkuRechargeEntity skuRechargeEntity);

}