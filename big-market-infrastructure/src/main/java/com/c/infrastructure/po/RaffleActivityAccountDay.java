package com.c.infrastructure.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 用户活动日账户持久化对象
 * 职责：细粒度控制用户每日参与频次，防止单日流量被恶意刷取。
 *
 * @author cyh
 * @date 2026/02/01
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RaffleActivityAccountDay {

    /** 自增主键 */
    private Long id;

    /** 用户标识 */
    private String userId;

    /** 活动标识 */
    private Long activityId;

    /** 统计日期（格式：yyyy-mm-dd） */
    private String day;

    /** 当日总可用次数 */
    private Integer dayCount;

    /** 当日剩余可抽奖次数 */
    private Integer dayCountSurplus;

    /** 创建时间 */
    private Date createTime;

    /** 更新时间 */
    private Date updateTime;

}