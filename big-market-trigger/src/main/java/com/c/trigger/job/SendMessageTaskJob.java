package com.c.trigger.job;

import com.c.domain.task.model.entity.TaskEntity;
import com.c.domain.task.service.ITaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务补偿定时任务
 * 1. 最终一致性扫描：基于 Transactional Outbox 模式，通过定时轮询补发因网络波动等原因未发送成功的领域消息。
 * 2. 跨分片自动路由：依托 ShardingSphere 代理，实现对全量物理库表（Task Table）的透明化扫描。
 * 3. 异步并发投递：结合线程池资源，实现高吞吐的消息状态推送到 MQ 队列。
 *
 * @author cyh
 * @date 2026/02/01
 */
@Slf4j
@Component
public class SendMessageTaskJob {

    @Resource
    private ITaskService taskService;

    @Resource
    private ThreadPoolExecutor executor;

    /**
     * 执行消息补偿扫描
     * 运行频率：每 5 秒触发一次。
     * 1. 查询阶段：由于查询 SQL 不含 userId 分片键，ShardingSphere 会触发“广播查询”，并发扫描所有底层分片表。
     * 2. 处理阶段：将逻辑结果集汇总后，利用线程池多线程并行外发消息。
     * 3. 更新阶段：更新状态时必须回传 userId，确保 ShardingSphere 执行“精准写”，避免全量扫描性能损耗。
     */
    @Scheduled(cron = "0 */1 * * * ?")
    public void exec() {
        try {
            // 1. 扫描待投递任务（ShardingSphere 广播扫描全量分片库表）
            List<TaskEntity> taskEntities = taskService.queryNoSendMessageTaskList();
            if (taskEntities == null || taskEntities.isEmpty()) {
                return;
            }

            // 2. 遍历任务队列，交由线程池处理异步投递逻辑
            for (TaskEntity taskEntity : taskEntities) {
                executor.execute(() -> {
                    try {
                        // 2.1 驱动领域服务外发消息至 MQ
                        taskService.sendMessage(taskEntity);

                        /*
                         * 2.2 更新状态为成功
                         * 必须回传 userId (分片键)。
                         * ShardingSphere 会解析 userId 直接定位到物理库和物理表，执行精准更新，提升性能并减少锁竞争。
                         */
                        taskService.updateTaskSendMessageCompleted(taskEntity.getUserId(),
                                taskEntity.getMessageId());

                        log.info("定时任务补偿成功 userId: {} messageId: {}", taskEntity.getUserId(),
                                taskEntity.getMessageId());
                    } catch (Exception e) {
                        log.error("定时任务补偿失败 userId: {} topic: {}", taskEntity.getUserId(),
                                taskEntity.getTopic(), e);

                        // 2.3 状态置回失败状态，等待下一次轮询窗口重试
                        taskService.updateTaskSendMessageFail(taskEntity.getUserId(),
                                taskEntity.getMessageId());
                    }
                });
            }
        } catch (Exception e) {
            log.error("分布式定时任务扫描异常", e);
        }
    }
}