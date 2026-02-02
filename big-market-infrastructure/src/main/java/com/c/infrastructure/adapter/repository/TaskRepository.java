package com.c.infrastructure.adapter.repository;

import com.c.domain.task.model.entity.TaskEntity;
import com.c.domain.task.repository.ITaskRepository;
import com.c.infrastructure.dao.ITaskDao;
import com.c.infrastructure.event.EventPublisher;
import com.c.infrastructure.po.Task;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * 任务补偿仓储实现类
 * 1. 最终一致性保障：基于“本地消息表”模式，处理因 MQ 投递失败或宕机导致的领域事件丢失问题。
 * 2. 状态机管理：维护任务从“待发送”到“发送成功/失败”的状态流转。
 * 3. 读写分离路由：配合 ShardingSphere，确保任务记录的读写均落在用户所属的分库分表中。
 *
 * @author cyh
 * @date 2026/02/01
 */
@Repository
public class TaskRepository implements ITaskRepository {

    @Resource
    private ITaskDao taskDao;
    @Resource
    private EventPublisher eventPublisher;

    /**
     * 查询未成功发送的消息任务列表
     * 场景：通常由分布式任务调度（如 XXL-JOB）调用，扫描状态为“待发送”或“发送失败”的任务进行补偿投递。
     * 注意：在 ShardingSphere 场景下，此查询不带分片键，会触发全库全表广播查询。
     *
     * @return 待补偿的任务实体列表
     */
    @Override
    public List<TaskEntity> queryNoSendMessageTaskList() {
        // 1. 从数据库查询 PO 列表
        List<Task> tasks = taskDao.queryNoSendMessageTaskList();
        if (tasks == null || tasks.isEmpty()) return new ArrayList<>();

        // 2. 转换对象（建议增加对 MessageBody 的反序列化处理，如果 Entity 中定义的是对象的话）
        List<TaskEntity> taskEntities = new ArrayList<>(tasks.size());
        for (Task task : tasks) {
            taskEntities.add(TaskEntity.builder().userId(task.getUserId()).topic(task.getTopic())
                                       // 如果 TaskEntity 的 message 字段是具体对象，这里需要反序列化
                                       .messageId(task.getMessageId()).message(task.getMessage()).build());
        }
        return taskEntities;
    }

    /**
     * 执行消息发送动作
     * 逻辑：调用基础设施层的 EventPublisher 投递消息至 MQ 中间件。
     *
     * @param taskEntity 包含 Topic、Message 内容的任务实体
     */
    @Override
    public void sendMessage(TaskEntity taskEntity) {
        eventPublisher.publish(taskEntity.getTopic(), taskEntity.getMessage());
    }

    /**
     * 更新任务状态为：发送成功
     * 约束：必须传入 userId 以便 ShardingSphere 精准路由到对应的用户分片库。
     *
     * @param userId    用户ID（分片键）
     * @param messageId 消息唯一标识
     */
    @Override
    public void updateTaskSendMessageCompleted(String userId, String messageId) {
        Task taskReq = new Task();
        taskReq.setUserId(userId);
        taskReq.setMessageId(messageId);
        taskDao.updateTaskSendMessageCompleted(taskReq);
    }

    /**
     * 更新任务状态为：发送失败
     * 备注：失败后的任务将由补偿 Job 进行下一轮重试，直到达到最大重试次数。
     *
     * @param userId    用户ID（分片键）
     * @param messageId 消息唯一标识
     */
    @Override
    public void updateTaskSendMessageFail(String userId, String messageId) {
        Task taskReq = new Task();
        taskReq.setUserId(userId);
        taskReq.setMessageId(messageId);
        taskDao.updateTaskSendMessageFail(taskReq);
    }
}