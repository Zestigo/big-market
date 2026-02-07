package com.c.infrastructure.dao;

import com.c.infrastructure.po.Task;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 任务表数据访问层 (DAO) - 本地消息表模式核心组件
 * 1. 记录与业务操作同事务的待发送消息（本地消息表），确保业务与消息发送的原子性。
 * 2. 支撑后台定时任务（Job）扫描未成功发送的消息，实现消息的可靠性投递（最终一致性）。
 * - CREATE -> COMPLETED (发送成功)
 * - CREATE -> fail (发送失败)
 *
 * @author cyh
 * @date 2026/02/03
 */
@Mapper
public interface ITaskDao {

    /**
     * 插入任务消息（通常随业务事务开启）
     *
     * @param task 任务对象，需包含消息主题、序列化后的内容以及分片键 user_id
     */
    void insert(Task task);

    /**
     * 更新任务状态为：发送成功
     * 逻辑：通过 userId 和 taskId 双重确认，更新状态为 COMPLETED
     *
     * @param task 包含 userId, messageId 的任务对象
     */
    void updateTaskSendMessageCompleted(Task task);

    /**
     * 更新任务状态为：发送失败
     *
     * @param task 包含 userId, messageId 的任务对象
     */
    void updateTaskSendMessageFail(Task task);

    /**
     * 查询未发送成功的任务列表（扫描扫描异常任务）
     * 用于补偿 Job 扫描：提取状态为 CREATE 且满足重试间隔的任务，或状态为 fail 的任务
     *
     * @return 待补偿任务列表，通常限制单次提取数量（如 limit 10）
     */
    List<Task> queryNoSendMessageTaskList();

}