package com.c.infrastructure.po;

import lombok.Data;
import java.util.Date;

/**
 * 活动参与次数配置持久化对象
 * 职责：作为配置模板，定义某一类活动默认的总、日、月抽奖限制数。
 *
 * @author cyh
 * @date 2026/01/25
 */
@Data
public class RaffleActivityCount {

    /** 自增ID */
    private Long id;

    /** 活动次数编号（配置标识） */
    private Long activityCountId;

    /** 限制的总次数 */
    private Integer totalCount;

    /** 限制的日次数 */
    private Integer dayCount;

    /** 限制的月次数 */
    private Integer monthCount;

    /** 创建时间 */
    private Date createTime;

    /** 更新时间 */
    private Date updateTime;

}