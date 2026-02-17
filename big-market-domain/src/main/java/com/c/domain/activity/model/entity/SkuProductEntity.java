package com.c.domain.activity.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * SKU 产品实体对象
 *
 * @author cyh
 * @date 2026/02/16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkuProductEntity {

    /* 商品 SKU 唯一标识 */
    private Long sku;

    /* 关联的活动 ID */
    private Long activityId;

    /* 关联的活动次数配置 ID */
    private Long activityCountId;

    /* 原始库存总量 */
    private Integer stockCount;

    /* 当前可用剩余库存 */
    private Integer stockCountSurplus;

    /* 兑换该商品所需的积分金额 */
    private BigDecimal productAmount;

    /* 活动购买后可获得的次数限制配置 */
    private ActivityCount activityCount;

    /**
     * 活动次数核销详情实体
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActivityCount {

        /* 允许参与的总次数 */
        private Integer totalCount;

        /* 每日允许参与的次数 */
        private Integer dayCount;

        /* 每月允许参与的次数 */
        private Integer monthCount;
    }
}