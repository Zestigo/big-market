package com.c.domain.credit.model.entity;

import com.c.domain.credit.event.CreditAdjustSuccessMessageEvent;
import com.c.domain.credit.model.vo.TaskStateVO;
import com.c.types.event.BaseEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 任务实体对象
 * 用于持久化记录消息发送任务，支撑分布式事务中的本地消息表模式。
 *
 * @author cyh
 * @date 2026/02/09
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TaskEntity {

    /** 用户唯一 ID：标识奖品归属方 */
    private String userId;

    /** 消息队列交换机名称 (Exchange)：指定发奖消息的入口 */
    private String exchange;

    /** 消息队列路由键 (Routing Key)：指定发奖消息的精准投递路径 */
    private String routingKey;

    /** 消息唯一编号：作为全局幂等标识，防止奖品重复发放 */
    private String messageId;

    /** 消息主体内容 */
    private BaseEvent.EventMessage<CreditAdjustSuccessMessageEvent.CreditAdjustSuccessMessage> message;

    /** 任务状态；create-创建、completed-完成、fail-失败 */
    private TaskStateVO state;

}