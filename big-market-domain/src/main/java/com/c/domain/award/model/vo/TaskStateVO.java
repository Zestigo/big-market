package com.c.domain.award.model.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 任务补偿状态枚举值对象
 * 1. 状态机定义：描述分布式消息任务在“本地消息表”模式下的完整生命周期。
 * 2. 路由依据：作为补偿 Job 扫描逻辑的过滤条件，决定哪些消息需要重试投递。
 *
 * @author cyh
 * @date 2026/02/01
 */
@Getter
@AllArgsConstructor
public enum TaskStateVO {

    /**
     * 已创建：消息已随业务事务同步入库，等待初始尝试投递。
     * 备注：若初始投递失败或系统宕机，补偿 Job 将扫描此状态的任务进行重试。
     */
    create("create", "创建"),

    /**
     * 发送完成：消息已成功投递至 MQ 中间件，且已收到 Ack 确认。
     * 备注：处于此状态的任务将不再被补偿 Job 扫描。
     */
    complete("complete", "发送完成"),

    /**
     * 发送失败：消息投递过程中发生明确异常。
     * 备注：该状态用于标记异常任务，补偿 Job 会对其实施退避重试策略。
     */
    fail("fail", "发送失败"),
    ;

    /** 状态枚举编码 */
    private final String code;

    /** 状态描述说明 */
    private final String desc;

}