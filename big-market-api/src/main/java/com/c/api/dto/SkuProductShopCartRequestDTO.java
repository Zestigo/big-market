package com.c.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

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
public class SkuProductShopCartRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /* 外部业务单号 */
    private String outBusinessNo;

    /* 用户ID */
    private String userId;

    /* 商品SKU */
    private Long sku;

    /* 购买数量 */
    private Integer quantity;

}