package com.c.trigger.job;

import com.c.domain.strategy.model.vo.StrategyAwardStockKeyVO;
import com.c.domain.strategy.service.IRaffleStock;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 * 基础设施层：策略奖品库存异步同步任务（XXL-JOB + Redisson 优化版）
 * 职责：将 Redis 中预扣减的逻辑库存状态，异步刷新至数据库物理表，实现最终一致性。
 *
 * @author cyh
 * @date 2026/03/10
 */
@Slf4j
@Component
public class UpdateStrategyAwardStockJob {

    @Resource
    private IRaffleStock raffleStock;

    @Resource
    private RedissonClient redissonClient;

    /**
     * 执行库存同步任务
     * 1. 分布式锁：利用 Redisson 抢占锁，防止多台机器同时消费同一个 Redis 队列导致写竞争。
     * 2. 消息驱动：从 Redis 阻塞队列中持续提取待更新标识。
     * 3. 容错处理：单条记录更新失败抛出异常记录，但不中断整体循环。
     */
    @XxlJob("updateStrategyAwardStockJobHandler")
    public void exec() throws InterruptedException {
        // [步骤 1] 分布式锁抢占：确保集群环境下仅有一个节点执行
        RLock lock = redissonClient.getLock("big-market-updateStrategyAwardStockJob");
        boolean isLocked = false;
        try {
            // 尝试加锁 3s，加锁成功后由 finally 释放
            isLocked = lock.tryLock(3, 0, TimeUnit.SECONDS);
            if (!isLocked) {
                log.warn(">>>>>> XXL-JOB 策略奖品库存同步任务抢占锁失败，本次跳过执行");
                return;
            }

            log.info(">>>>>> XXL-JOB 策略奖品库存同步任务开始执行");

            // [步骤 2] 循环提取待处理库存消息，直至队列清空
            while (true) {
                // 从领域层提供的消费接口获取待更新对象
                StrategyAwardStockKeyVO strategyAwardStockKeyVO = raffleStock.takeQueueValue();

                // 队列为空则退出当前任务周期
                if (null == strategyAwardStockKeyVO) {
                    log.info(">>>>>> 本次调度库存更新队列已清空");
                    break;
                }

                // [步骤 3] 执行数据库库存同步逻辑
                try {
                    log.info("开始同步奖品物理库存 | strategyId: {} | awardId: {}", strategyAwardStockKeyVO.getStrategyId(),
                            strategyAwardStockKeyVO.getAwardId());

                    // 核心动作：更新数据库中 strategy_award 表的物理库存
                    raffleStock.updateStrategyAwardStock(strategyAwardStockKeyVO.getStrategyId(),
                            strategyAwardStockKeyVO.getAwardId());

                } catch (Exception e) {
                    log.error("同步奖品物理库存失败 | strategyId: {} | awardId: {}", strategyAwardStockKeyVO.getStrategyId(),
                            strategyAwardStockKeyVO.getAwardId(), e);
                }
            }
        } catch (Exception e) {
            log.error("策略奖品库存异步更新任务执行严重异常", e);
            throw e;
        } finally {
            // [步骤 4] 释放锁
            if (isLocked) {
                lock.unlock();
            }
        }
    }
}