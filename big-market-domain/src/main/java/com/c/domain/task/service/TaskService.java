package com.c.domain.task.service;

import com.c.domain.task.model.entity.TaskEntity;
import com.c.domain.task.repository.ITaskRepository;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * 任务补偿领域服务
 * 1. 消息补偿调度：作为领域层的核心入口，负责驱动那些因瞬时网络抖动或宕机导致的待发送消息。
 * 2. 状态闭环管理：协调仓储层完成从“任务扫描 -> 消息外发 -> 状态回写”的完整业务闭环。
 * 3. 最终一致性核心：是实现本地消息表模式（Transactional Outbox）中异步扫描逻辑的关键组件。
 *
 * @author cyh
 * @date 2026/02/01
 */
@Service
public class TaskService implements ITaskService {

    @Resource
    private ITaskRepository taskRepository;

    /**
     * 查询未成功发送的消息任务列表
     * 由补偿 Job 定时触发，用于拉取数据库中状态为“待发送”或“发送失败”的任务记录。
     * 通常配合分页或限流扫描，防止单次扫描数据量过大导致内存溢出。
     *
     * @return 待发送任务实体列表
     */
    @Override
    public List<TaskEntity> queryNoSendMessageTaskList() {
        return taskRepository.queryNoSendMessageTaskList();
    }

    /**
     * 执行消息投递
     * 驱动基础设施层将任务内容推送到消息队列（MQ）。此操作本身不保证幂等，
     * 需配合后续的状态更新（Completed）来降低任务重复扫描的概率。
     *
     * @param taskEntity 包含 Topic、MessageBody 等信息的任务实体
     */
    @Override
    public void sendMessage(TaskEntity taskEntity) {
        taskRepository.sendMessage(taskEntity);
    }

    /**
     * 更新任务为“发送完成”状态
     * 必须传递 userId 字段，以确保在分库分表环境下，ShardingSphere 能够通过分片键精准定位物理表。
     *
     * @param userId    用户ID（分片键）
     * @param messageId 消息唯一ID
     */
    @Override
    public void updateTaskSendMessageCompleted(String userId, String messageId) {
        taskRepository.updateTaskSendMessageCompleted(userId, messageId);
    }

    /**
     * 更新任务为“发送失败”状态
     * 标记失败后，任务将停留在库中，等待下一轮补偿 Job 的轮询重试，直到达到系统预设的最大重试阈值。
     *
     * @param userId    用户ID（分片键）
     * @param messageId 消息唯一ID
     */
    @Override
    public void updateTaskSendMessageFail(String userId, String messageId) {
        taskRepository.updateTaskSendMessageFail(userId, messageId);
    }

}