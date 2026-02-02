package com.c.domain.task.repository;

import com.c.domain.task.model.entity.TaskEntity;

import java.util.List;

/**
 * 任务补偿仓储接口
 * 1. 消息可靠性保障：定义了任务表（Task Table）的操作标准，用于解决领域事件在分布式环境下的丢失问题。
 * 2. 状态一致性维护：支持任务从“初始化”到“发送成功”或“失败重试”的状态流转。
 * 3. 跨分片数据契约：为上层提供统一的接口，隐藏 ShardingSphere 在多分片库表下的数据读写细节。
 *
 * @author cyh
 * @date 2026/02/02
 */
public interface ITaskRepository {

    /**
     * 查询未成功发送的消息任务列表
     * 场景：通常由分布式定时任务驱动，通过全量（广播）扫描各分片表，提取状态为待补偿的任务。
     * 约束：返回的实体应包含 userId（分片键），以便后续步骤能精准定位原始库表。
     *
     * @return 待发送任务实体集合
     */
    List<TaskEntity> queryNoSendMessageTaskList();

    /**
     * 执行消息发送动作
     * 职责：驱动基础设施层（如 RabbitMQ/RocketMQ）执行消息推送。
     * 风险：此操作非幂等，下游消费方需根据 TaskEntity.messageId 实现业务幂等逻辑。
     *
     * @param taskEntity 包含消息内容与路由信息的任务实体
     */
    void sendMessage(TaskEntity taskEntity);

    /**
     * 更新任务状态为：发送成功
     * 1. 路由精准：在 ShardingSphere 架构下，必须显式传入 userId 作为分片键，以避免全局扫描。
     * 2. 状态终结：标记后，该任务将不再被补偿 Job 扫描。
     *
     * @param userId    用户ID（分片键）
     * @param messageId 消息全局唯一ID
     */
    void updateTaskSendMessageCompleted(String userId, String messageId);

    /**
     * 更新任务状态为：发送失败
     * 场景：当 MQ 服务暂时不可用或网络异常时调用。
     * 备注：失败的任务将停留在库中，等待下一次轮询窗口进行阶梯式重试。
     *
     * @param userId    用户ID（分片键）
     * @param messageId 消息全局唯一ID
     */
    void updateTaskSendMessageFail(String userId, String messageId);

}