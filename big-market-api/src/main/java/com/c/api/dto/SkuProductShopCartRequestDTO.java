package com.c.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SKU产品购物车请求 DTO
 *
 * @author cyh
 * @date 2026/02/16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkuProductShopCartRequestDTO {

    /* 用户唯一标识 ID (通常为 Long 类型或 String 格式的 UUID) */
    private String userId;

    /* 商品 SKU 唯一标识 ID */
    private Long sku;

    /* 商品购买数量 (建议补充，购物车通常需要传量) */
    private Integer quantity;
}