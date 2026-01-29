package com.c.domain.activity.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @description 活动参与充值实体（SKU 充值意图对象）
 * 1. 业务定义：描述了“谁（userId）”、“通过什么路径（sku）”、“根据哪个凭证（outBusinessNo）”来获取活动参与次数。
 * 2. 核心作用：作为领域服务的入参，驱动账户额度的增加以及充值流水的记录。
 *
 * @author cyh
 * @date 2026/01/27
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SkuRechargeEntity {

    /**
     * 用户唯一标识 ID
     * 对应抽奖系统中的账户归属者。
     */
    private String userId;

    /**
     * 活动 SKU 编码
     * 逻辑含义：Activity + Activity Count 的组合。
     * 通过 SKU 可以定位到具体的活动 ID 以及该 SKU 对应的充值次数（如：购买 SKU_01 充值 10 次）。
     */
    private Long sku;

    /**
     * 外部业务单号（幂等单号）
     * 1. 来源：由外部调用方（如支付系统、任务系统）透传。
     * 2. 作用：用于保证充值操作的【幂等性】。
     * 3. 逻辑：在处理充值请求时，系统会校验该单号是否已处理，防止因网络抖动、重试机制导致的重复加次数。
     */
    private String outBusinessNo;

}