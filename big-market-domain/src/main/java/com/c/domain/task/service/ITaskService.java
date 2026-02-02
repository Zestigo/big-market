package com.c.domain.task.service;

import com.c.domain.task.model.entity.TaskEntity;

import java.util.List;

/**
 * 任务补偿领域服务接口
 * 1. 最终一致性保障：定义了本地消息表（Transactional Outbox）模式下，任务从扫描、投递到状态反馈的标准链路。
 * 2. 消息可靠性契约：确保在 MQ 瞬时不可用或分布式事务执行中断时，有据可查并可被重新驱动。
 *
 * @author cyh
 * @date 2026/02/01
 */
public interface ITaskService {

    /**
     * 获取待补偿的消息任务列表
     * 该方法应扫描数据库中状态为“待发送（unsent）”或“发送失败（fail）”的任务。
     * 建议实现类在查询时加入时间窗口限制（如扫描 1 分钟前的任务），避免与刚入库的实时事务冲突。
     *
     * @return 待发送任务实体集合
     */
    List<TaskEntity> queryNoSendMessageTaskList();

    /**
     * 执行消息发送逻辑
     * 驱动基础设施层的消息中间件（如 RabbitMQ）执行消息投递。
     * 警告：该操作可能导致消息重复投递，下游消费端必须实现基于 messageId 的幂等校验。
     *
     * @param taskEntity 任务信息实体，包含消息 Topic 和 Payload
     */
    void sendMessage(TaskEntity taskEntity);

    /**
     * 标记任务发送成功
     * 在分库分表环境下，userId 作为分片键必须传入，以支持 ShardingSphere 精准定位物理表，避免全库扫描。
     * 成功状态的任务将不再被补偿 Job 扫描。
     *
     * @param userId    用户唯一标识（分片键）
     * @param messageId 消息全局唯一标识
     */
    void updateTaskSendMessageCompleted(String userId, String messageId);

    /**
     * 记录任务发送失败
     * 标记该任务本次投递尝试失败，待下一次 Job 轮询周期再次重试。
     *
     * @param userId    用户唯一标识（分片键）
     * @param messageId 消息全局唯一标识
     */
    void updateTaskSendMessageFail(String userId, String messageId);

}