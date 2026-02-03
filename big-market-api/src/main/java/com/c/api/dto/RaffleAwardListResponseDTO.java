package com.c.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 抽奖奖品列表查询响应对象
 *
 * @author cyh
 * @since 2026/02/02
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RaffleAwardListResponseDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 奖品 ID */
    private Integer awardId;

    /** 奖品标题 */
    private String awardTitle;

    /** 奖品副标题 (例如：抽奖n次后解锁) */
    private String awardSubtitle;

    /** 奖品展示排序编号 */
    private Integer sort;

}