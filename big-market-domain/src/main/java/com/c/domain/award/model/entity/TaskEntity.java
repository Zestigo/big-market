package com.c.domain.award.model.entity;

import com.c.domain.award.event.SendAwardMessageEvent;
import com.c.domain.award.model.vo.TaskStateVO;
import com.c.types.event.BaseEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 奖品发放任务实体（领域对象）
 * 职责：作为领域层与基础设施层之间的传输载体，承载可靠消息投递的核心要素。
 * 作用：记录奖品发放事件的完整上下文，包括路由配置、幂等 ID 及业务负载，支撑事务消息方案。
 *
 * @author cyh
 * @date 2026/02/01
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

    /** 消息包装主体：包含标准元数据与发奖业务载荷（SendAwardMessage） */
    private BaseEvent.EventMessage<SendAwardMessageEvent.SendAwardMessage> message;

    /** 任务状态：执行状态流转（CREATE-初始、COMPLETED-完成、fail-失败） */
    private TaskStateVO state;

}