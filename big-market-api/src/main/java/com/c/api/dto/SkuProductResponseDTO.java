package com.c.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * SKU产品响应结果 DTO
 *
 * @author cyh
 * @date 2026/02/16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkuProductResponseDTO implements Serializable {

    /* 商品 SKU 唯一标识 ID */
    private Long sku;

    /* 关联的活动唯一标识 ID */
    private Long activityId;

    /* 活动个人参与次数统计 ID */
    private Long activityCountId;

    /* 该商品的库存总数量 */
    private Integer stockCount;

    /* 扣减后剩余的可用库存数量 */
    private Integer stockCountSurplus;

    /* 商品兑换所需的金额或积分数值 */
    private BigDecimal productAmount;

    /* 活动参与次数限制详情 */
    private ActivityCount activityCount;

    /**
     * 活动参与次数详情类
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActivityCount {

        /* 允许参与的总次数上限 */
        private Integer totalCount;

        /* 每日允许参与的次数上限 */
        private Integer dayCount;

        /* 每月允许参与的次数上限 */
        private Integer monthCount;
    }
}