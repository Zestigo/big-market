package com.c.domain.activity.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 投递单实体对象
 * 用于记录外部透传的业务奖励、行为返利等投递信息，支持幂等校验。
 *
 * @author cyh
 * @date 2026/02/09
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DeliveryOrderEntity {

    /** 用户ID */
    private String userId;

    /** 业务仿重ID，外部透传的唯一标识（如返利、行为标识） */
    private String outBusinessNo;

}