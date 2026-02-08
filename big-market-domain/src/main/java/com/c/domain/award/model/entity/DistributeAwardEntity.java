package com.c.domain.award.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 分发奖品领域实体对象
 *
 * @author cyh
 * @date 2026/02/07
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DistributeAwardEntity {

    /** 用户ID */
    private String userId;

    /** 关联单号（幂等索引） */
    private String orderId;

    /** 奖品ID */
    private Integer awardId;

    /** 奖品配置（积分值、SKU等） */
    private String awardConfig;

}