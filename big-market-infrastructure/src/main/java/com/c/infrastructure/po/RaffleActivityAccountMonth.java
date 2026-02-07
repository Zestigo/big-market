package com.c.infrastructure.po;

import lombok.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;


/**
 * 用户活动月账户实体
 *
 * @author cyh
 * @date 2026/02/05
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RaffleActivityAccountMonth {

    // 使用线程安全的 DateTimeFormatter 替代 SimpleDateFormat
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    /** 自增主键 */
    private Long id;
    /** 用户标识 */
    private String userId;
    /** 活动标识 */
    private Long activityId;
    /** 统计月份（格式：yyyy-MM） */
    private String month;
    /** 当前月总可用次数 */
    private Integer monthCount;
    /** 当前月剩余可抽奖次数 */
    private Integer monthCountSurplus;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 获取当前月份
     */
    public static String currentMonth() {
        return LocalDateTime.now().format(MONTH_FORMATTER);
    }

    /**
     * 检查当前月是否还有剩余次数
     */
    public boolean hasSurplus() {
        return this.monthCountSurplus != null && this.monthCountSurplus > 0;
    }

    /**
     * 扣减月次数
     * 封装状态变更行为，防止外部随意修改字段
     */
    public void decreaseCount() {
        if (!hasSurplus()) {
            throw new RuntimeException("当前月剩余次数不足");
        }
        this.monthCountSurplus--;
    }
}