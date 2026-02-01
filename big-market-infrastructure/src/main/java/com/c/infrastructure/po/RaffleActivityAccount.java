package com.c.infrastructure.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 用户活动账户持久化对象
 * 职责：维护用户在特定活动下的参与额度。支持总额度、日额度、月额度的多重校验。
 *
 * @author cyh
 * @date 2026/01/25
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RaffleActivityAccount {

    /** 自增ID */
    private Long id;

    /** 用户ID */
    private String userId;

    /** 活动ID */
    private Long activityId;

    /** 总次数额度（活动期间总计可用次数） */
    private Integer totalCount;

    /** 总次数剩余 */
    private Integer totalCountSurplus;

    /** 日次数额度（单日上限） */
    private Integer dayCount;

    /** 日次数剩余 */
    private Integer dayCountSurplus;

    /** 月次数额度（单月上限） */
    private Integer monthCount;

    /** 月次数剩余 */
    private Integer monthCountSurplus;

    /** 创建时间 */
    private Date createTime;

    /** 更新时间 */
    private Date updateTime;

}