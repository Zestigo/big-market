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

    /** 奖品 ID */
    private Integer awardId;

    /** 奖品标题 */
    private String awardTitle;

    /** 奖品副标题 (例如：抽奖n次后解锁) */
    private String awardSubtitle;

    /** 奖品展示排序编号 */
    private Integer sort;

    /** 奖品规则锁定的抽奖次数阈值 */
    private Integer awardRuleLockCount;

    /** 奖品是否已解锁 (true: 已解锁, false: 未解锁) */
    private Boolean isAwardUnlock;

    /** 距离解锁还需要抽奖的次数 */
    private Integer waitUnlockCount;

}