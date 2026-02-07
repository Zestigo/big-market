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
 * 分布式定时任务：异步消息补偿投递
 * <p>
 * 职责：
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
     * <p>
     * 1. 查询阶段：由于查询 SQL 不含 userId，ShardingSphere 会触发“广播查询”，并发扫描所有底层分片表。
     * 2. 处理阶段：将结果汇总后，利用线程池多线程并行外发消息，防止单线程阻塞。
     * 3. 更新阶段：更新状态时回传 userId（分片键），确保 Sharding执行“精准写”，避免全表扫描损耗性能。
     */
    @Scheduled(cron = "0/5 * * * * ?")
    public void exec() {
        try {
            // 1. 扫描待投递任务（ShardingSphere 广播扫描全量分片库表）
            List<TaskEntity> taskEntities = taskService.queryNoSendMessageTaskList();
            if (taskEntities == null || taskEntities.isEmpty()) {
                return;
            }

            // 2. 遍历任务，交由线程池处理
            for (TaskEntity taskEntity : taskEntities) {
                executor.execute(() -> {
                    try {
                        // 2.1 驱动领域服务外发消息至 MQ
                        taskService.sendMessage(taskEntity);

                        // 2.2 更新状态为成功（回传 userId 确保分片精准路由）
                        taskService.updateTaskSendMessageCompleted(taskEntity.getUserId(), taskEntity.getMessageId());

                        log.info("定时任务补偿成功 | userId: {} | messageId: {}", taskEntity.getUserId(),
                                taskEntity.getMessageId());
                    } catch (Exception e) {
                        log.error("定时任务补偿失败 | userId: {} | exchange: {}", taskEntity.getUserId(),
                                taskEntity.getExchange(), e);

                        // 2.3 状态置回失败状态，等待重试窗口
                        taskService.updateTaskSendMessageFail(taskEntity.getUserId(), taskEntity.getMessageId());
                    }
                });
            }
        } catch (Exception e) {
            log.error("分布式定时任务扫描异常", e);
        }
    }
}