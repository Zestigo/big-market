package com.c.domain.task.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 消息任务补偿领域实体
 * 职责：作为任务调度（Job）与执行引擎之间的核心契约对象。
 * 特点：承载了消息投递所需的完整路由要素，确保补偿发送时能够精准还原原始投递路径。
 *
 * @author cyh
 * @date 2026/02/01
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TaskEntity {

    /** 用户唯一 ID：作为业务关联标识及分库分表的分片键 */
    private String userId;

    /** 消息队列交换机 (Exchange)：定义消息的投递入口 */
    private String exchange;

    /** 消息队列路由键 (Routing Key)：定义消息的分发规则 */
    private String routingKey;

    /** 消息唯一编号：用于投递过程中的幂等性校验与状态追踪 */
    private String messageId;

    /** 消息主体：已序列化的业务数据报文（通常为 JSON 格式） */
    private String message;

}