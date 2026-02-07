package com.c.infrastructure.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * 用户活动日账户持久化对象（充血模型）
 * 对应数据库表：raffle_activity_account_day
 * 职责：作为日维度额度的聚合根数据载体，负责维护用户每日参与频次的原子状态。
 *
 * @author cyh
 * @since 2026/02/01
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RaffleActivityAccountDay implements Serializable {

    /** 序列化版本标识符，确保跨进程通信（如缓存、RPC）时的类版本兼容性 */
    private static final long serialVersionUID = 1L;

    /** * 线程安全的日期格式化器 (yyyy-MM-dd)
     * 替换原非线程安全的 SimpleDateFormat，确保在并发环境下日期转换的准确性
     */
    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 自增主键 (物理主键) */
    private Long id;

    /** 用户唯一标识：通过该 ID 锁定特定用户的账户记录 */
    private String userId;

    /** 活动唯一标识：区分不同营销活动的额度统计 */
    private Long activityId;

    /** 统计日期：业务主键维度，存储格式为 yyyy-MM-dd，用于隔离不同日期的额度 */
    private String day;

    /** 当日总次数上限：由活动 SKU 配置决定的初始总配额 */
    private Integer dayCount;

    /** 当日剩余可抽奖次数：核心动态字段，代表当前时刻用户还能参与的次数 */
    private Integer dayCountSurplus;

    /** 记录创建时间 */
    private Date createTime;

    /** 记录最近一次变动（额度扣减/补偿）的更新时间 */
    private Date updateTime;

    /**
     * 行为方法：获取当前业务所需的日期字符串
     * 在充血模型中，此方法封装了日期生成的领域逻辑，统一业务维度的格式化标准。
     *
     * @return 当前日期，格式如 "2026-02-03"
     */
    public static String currentDay() {
        return LocalDate.now().format(DAY_FORMATTER);
    }
}