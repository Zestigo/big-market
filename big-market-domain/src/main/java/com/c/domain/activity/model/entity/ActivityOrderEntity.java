package com.c.domain.activity.model.entity;

import com.c.domain.activity.model.vo.OrderStateVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 活动参与订单实体对象
 * 记录用户参与活动产生的订单流水信息，用于持久化和状态追踪。
 *
 * @author cyh
 * @date 2026/01/27
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ActivityOrderEntity {

    /** 用户ID */
    private String userId;

    /** 活动ID */
    private Long activityId;

    /** 商品SKU */
    private Long sku;

    /** 活动名称 */
    private String activityName;

    /** 抽奖策略ID */
    private Long strategyId;

    /** 订单ID */
    private String orderId;

    /** 下单时间 */
    private Date orderTime;

    /** 业务仿重ID，用于确保幂等性 */
    private String outBusinessNo;

    /** 总次数限制 */
    private Integer totalCount;

    /** 日次数限制 */
    private Integer dayCount;

    /** 月次数限制 */
    private Integer monthCount;

    /** 支付金额 */
    private BigDecimal payAmount;

    /** 订单状态 */
    private OrderStateVO state;

}