package com.c.infrastructure.po;

import lombok.Data;
import java.util.Date;

/**
 * 用户活动月账户持久化对象
 * 职责：维护用户在特定活动下，按自然月划分的参与次数额度。
 *
 * @author cyh
 * @date 2026/02/01
 */
@Data
public class RaffleActivityAccountMonth {

    /** 自增主键 */
    private Long id;

    /** 用户标识 */
    private String userId;

    /** 活动标识 */
    private Long activityId;

    /** 统计月份（格式：yyyy-mm） */
    private String month;

    /** 当前月总可用次数 */
    private Integer monthCount;

    /** 当前月剩余可抽奖次数（随参与动作扣减） */
    private Integer monthCountSurplus;

    /** 创建时间 */
    private Date createTime;

    /** 更新时间 */
    private Date updateTime;

}