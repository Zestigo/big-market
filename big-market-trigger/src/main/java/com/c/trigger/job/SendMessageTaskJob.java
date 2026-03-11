package com.c.trigger.job;

import com.c.domain.task.model.entity.TaskEntity;
import com.c.domain.task.service.ITaskService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 分布式定时任务：异步消息补偿投递（XXL-JOB + Redisson 优化版）
 * 职责：基于 Transactional Outbox 模式，通过定时轮询补发因异常未发送成功的领域消息。
 *
 * @author cyh
 * @date 2026/03/10
 */
@Slf4j
@Component
public class SendMessageTaskJob {

    @Resource
    private ITaskService taskService;

    @Resource
    private ThreadPoolExecutor executor;

    @Resource
    private RedissonClient redissonClient;

    /**
     * 消息补偿任务
     * 优化点：引入分布式锁抢占机制，确保集群环境下仅有一台机器执行广播扫描，降低数据库压力。
     */
    @XxlJob("sendMessageTaskJobHandler")
    public void exec() throws InterruptedException {
        RLock taskJobLock = redissonClient.getLock("big-market-sendMessageTaskJob");
        boolean isJobLocked = false;
        List<TaskEntity> taskEntities = null;

        try {
            // 1. 抢「任务扫描锁」：仅保障单节点扫描
            isJobLocked = taskJobLock.tryLock(3, 180, TimeUnit.SECONDS);
            if (!isJobLocked) {
                log.warn(">>>>>> XXL-JOB 消息补偿任务抢占锁失败，本次跳过");
                return;
            }

            log.info(">>>>>> XXL-JOB 消息补偿任务开始执行");
            // 2. 扫描待投递任务：仅扫描阶段持有锁
            taskEntities = taskService.queryNoSendMessageTaskList();
            if (taskEntities == null || taskEntities.isEmpty()) {
                log.info(">>>>>> 本次扫描无待补偿任务");
                return;
            }
        } catch (Exception e) {
            log.error("XXL-JOB 分布式定时任务扫描异常", e);
            throw e;
        } finally {
            // 3. 扫描完成立即释放「任务级大锁」，不阻塞后续处理
            if (isJobLocked) {
                taskJobLock.unlock();
            }
        }

        // 4. 异步处理任务：给每个任务加「细粒度锁」，避免重复处理
        for (TaskEntity taskEntity : taskEntities) {
            executor.execute(() -> {
                // 任务级细粒度锁：key = 任务ID/消息ID，避免重复处理
                RLock taskLock = redissonClient.getLock("big-market-message-compensate-" + taskEntity.getMessageId());
                try {
                    // 尝试加锁，5s超时，自动释放1分钟
                    if (!taskLock.tryLock(5, 60, TimeUnit.SECONDS)) {
                        log.warn("任务已被其他线程处理 | messageId: {}", taskEntity.getMessageId());
                        return;
                    }
                    // 执行消息投递+状态更新
                    taskService.sendMessage(taskEntity);
                    taskService.updateTaskSendMessageCompleted(taskEntity.getUserId(), taskEntity.getMessageId());
                    log.info("定时任务补偿成功 | userId: {} | messageId: {}", taskEntity.getUserId(),
                            taskEntity.getMessageId());
                } catch (Exception e) {
                    log.error("定时任务补偿失败 | userId: {} | messageId: {}", taskEntity.getUserId(),
                            taskEntity.getMessageId(), e);
                    taskService.updateTaskSendMessageFail(taskEntity.getUserId(), taskEntity.getMessageId());
                } finally {
                    if (taskLock.isHeldByCurrentThread()) {
                        taskLock.unlock();
                    }
                }
            });
        }
    }
}