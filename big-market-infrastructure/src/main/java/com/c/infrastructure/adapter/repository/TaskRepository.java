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
import java.util.stream.Collectors;

/**
 * 任务补偿仓储实现类
 * 1. 故障恢复：作为“本地消息表”模式的执行引擎，负责恢复因 MQ 投递失败导致的信号丢失。
 * 2. 状态机驱动：管控任务从“CREATE/FAIL”到“COMPLETED”的流转。
 * 3. 跨库路由：配合分片键 userId，确保在 Sharding 场景下精准定位库表。
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
     * 说明：此处修复了原代码中未将实体加入集合的 Bug，并采用 Stream 风格使格式化更整齐。
     *
     * @return 待补偿任务实体列表
     */
    @Override
    public List<TaskEntity> queryNoSendMessageTaskList() {
        // 1. 从数据库读取 PO 列表
        List<Task> tasks = taskDao.queryNoSendMessageTaskList();
        if (tasks == null || tasks.isEmpty()) {
            return new ArrayList<>();
        }

        // 2. 转换 PO 为领域实体
        return tasks
                .stream()
                .map(task -> TaskEntity
                        .builder()
                        .userId(task.getUserId())
                        .exchange(task.getExchange())
                        .routingKey(task.getRoutingKey())
                        .messageId(task.getMessageId())
                        .message(task.getMessage())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 执行消息补偿发送
     * 调用基础设施层 EventPublisher，通过存储的 Exchange 和 RoutingKey 还原投递现场。
     *
     * @param taskEntity 包含完整路由信息的任务实体
     */
    @Override
    public void sendMessage(TaskEntity taskEntity) {
        eventPublisher.publish(taskEntity.getExchange(), taskEntity.getRoutingKey(), taskEntity.getMessage());
    }

    /**
     * 更新任务状态为：发送成功
     * 约束：必须包含分片键 userId，否则在分库分表环境下会触发全量扫描。
     *
     * @param userId    用户 ID（分片键）
     * @param messageId 消息唯一标识
     */
    @Override
    public void updateTaskSendMessageCompleted(String userId, String messageId) {
        Task task = Task
                .builder()
                .userId(userId)
                .messageId(messageId)
                .build();
        taskDao.updateTaskSendMessageCompleted(task);
    }

    /**
     * 更新任务状态为：发送失败
     *
     * @param userId    用户 ID（分片键）
     * @param messageId 消息唯一标识
     */
    @Override
    public void updateTaskSendMessageFail(String userId, String messageId) {
        Task task = Task
                .builder()
                .userId(userId)
                .messageId(messageId)
                .build();
        taskDao.updateTaskSendMessageFail(task);
    }
}