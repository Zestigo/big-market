package com.c.domain.rebate.model.entity;

import com.c.domain.rebate.event.SendRebateMessageEvent;
import com.c.domain.rebate.model.vo.TaskStateVO;
import com.c.types.event.BaseEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 任务补偿实体（用于消息发送与可靠性保证）
 * 核心逻辑：
 * 1. 变量名由 topic 改为 exchange，更贴合 RabbitMQ 架构。
 * 2. 显式包含 routingKey，确保消息投递的路径精确。
 *
 * @author cyh
 * @date 2026/02/05
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TaskEntity {

    /** 用户唯一ID */
    private String userId;

    /** 消息队列交换机名称 (原 topic) */
    private String exchange;

    /** 消息队列路由键 */
    private String routingKey;

    /** 消息唯一编号，用于发送幂等校验 */
    private String messageId;

    /** 消息主体（包含具体的返利事件数据） */
    private BaseEvent.EventMessage<SendRebateMessageEvent.RebateMessage> message;

    /** 任务执行状态（CREATE 初始、complete 完成、fail 失败） */
    private TaskStateVO state;

}