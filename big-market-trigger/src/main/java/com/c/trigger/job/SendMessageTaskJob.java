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
        // 1. 分布式锁抢占：确保多节点部署时，只有一个节点发起扫描
        RLock lock = redissonClient.getLock("big-market-sendMessageTaskJob");
        boolean isLocked = false;
        try {
            // 尝试加锁最多等待 3s，加锁成功后 0s（不设自动释放，靠 finally 释放）
            isLocked = lock.tryLock(3, 0, TimeUnit.SECONDS);
            if (!isLocked) {
                log.warn(">>>>>> XXL-JOB 消息补偿任务抢占锁失败，本次跳过");
                return;
            }

            log.info(">>>>>> XXL-JOB 消息补偿任务开始执行");

            // 2. 扫描待投递任务（ShardingSphere 广播扫描全量分片库表）
            List<TaskEntity> taskEntities = taskService.queryNoSendMessageTaskList();
            if (taskEntities == null || taskEntities.isEmpty()) {
                log.info(">>>>>> 本次扫描无待补偿任务");
                return;
            }

            // 3. 遍历任务，交由线程池处理
            for (TaskEntity taskEntity : taskEntities) {
                executor.execute(() -> {
                    try {
                        // 3.1 驱动领域服务外发消息至 MQ
                        taskService.sendMessage(taskEntity);

                        // 3.2 更新状态为成功（回传 userId 确保分片精准路由）
                        taskService.updateTaskSendMessageCompleted(taskEntity.getUserId(), taskEntity.getMessageId());

                        log.info("定时任务补偿成功 | userId: {} | messageId: {}", taskEntity.getUserId(),
                                taskEntity.getMessageId());
                    } catch (Exception e) {
                        log.error("定时任务补偿失败 | userId: {} | messageId: {}", taskEntity.getUserId(),
                                taskEntity.getMessageId(), e);

                        // 3.3 状态置回失败状态，等待重试窗口
                        taskService.updateTaskSendMessageFail(taskEntity.getUserId(), taskEntity.getMessageId());
                    }
                });
            }
        } catch (Exception e) {
            log.error("XXL-JOB 分布式定时任务扫描异常", e);
            throw e;
        } finally {
            // 4. 释放分布式锁
            if (isLocked) {
                lock.unlock();
            }
        }
    }
}