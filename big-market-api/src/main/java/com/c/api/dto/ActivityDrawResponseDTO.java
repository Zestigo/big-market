package com.c.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 活动抽奖响应对象
 *
 * @author cyh
 * @since 2026/02/02
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ActivityDrawResponseDTO implements Serializable {

    /** 奖品唯一标识 ID */
    private Integer awardId;

    /** 奖品名称/标题 */
    private String awardTitle;

    /** 奖品顺序索引 (对应抽奖策略中的排序编号) */
    private Integer awardIndex;

}