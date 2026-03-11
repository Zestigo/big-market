package com.c.trigger.job;

import com.c.domain.activity.model.vo.ActivitySkuStockKeyVO;
import com.c.domain.activity.service.IRaffleActivitySkuStockService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

/**
 * 基础设施层：活动 SKU 物理库存异步同步任务（XXL-JOB 分布式适配版）
 * * 核心设计：
 * 1. 分布式锁：利用 Redisson 确保多机部署时，同一时刻只有一个节点在执行同步，规避并发写竞争。
 * 2. 内存写合并：本周期内同一 SKU 的多次扣减在内存中 Merge 为一次更新，极大降低数据库行锁压力。
 * 3. 异步削峰：消费 Redis 队列数据，实现逻辑库存与物理库存的最终一致性。
 *
 * @author cyh
 * @date 2026/03/10
 */
@Slf4j
@Component
public class UpdateActivitySkuStockJob {

    @Resource
    private IRaffleActivitySkuStockService skuStock;

    @Resource
    private RedissonClient redissonClient;

    /**
     * 执行库存同步任务
     * 对应 XXL-JOB 后台 JobHandler: updateActivitySkuStockJobHandler
     */
    @XxlJob("updateActivitySkuStockJobHandler")
    public void exec() throws InterruptedException {
        // [步骤 1] 分布式锁抢占：防止集群环境下多台机器同时触发
        // 锁名称建议与业务挂钩，确保唯一性
        RLock lock = redissonClient.getLock("big-market-updateActivitySkuStockJob");
        boolean isLocked = false;
        try {
            // 尝试加锁，最多等待 3s（若有其他机器在跑则放弃），加锁成功后不设自动过期（靠 finally 释放）
            isLocked = lock.tryLock(3, 0, TimeUnit.SECONDS);
            if (!isLocked) {
                log.warn(">>>>>> XXL-JOB 活动 SKU 库存同步任务抢占锁失败，本次跳过执行");
                return;
            }

            log.info(">>>>>> XXL-JOB 活动 SKU 库存同步任务开始执行");

            // [步骤 2] 内存合并容器：Key = sku, Value = 本周期累计消耗量
            Map<Long, Integer> skuCountMap = new HashMap<>();

            // [步骤 3] 消费 Redis 队列：非阻塞提取消息，直至清空当前待处理指令
            while (true) {
                // 从领域层获取待同步对象（对应 Redis RBlockingQueue 或 List）
                ActivitySkuStockKeyVO vo = skuStock.takeQueueValue();
                if (null == vo) {
                    break;
                }
                // 【写合并优化】使用 Java8 merge 算子统计本轮累计消耗总数
                skuCountMap.merge(vo.getSku(), 1, Integer::sum);
            }

            // 无任务则直接结束
            if (skuCountMap.isEmpty()) {
                log.info(">>>>>> 本次调度无库存扣减指令，自动跳过");
                return;
            }

            // [步骤 4] 批量同步数据库：遍历合并后的结果集
            skuCountMap.forEach((sku, count) -> {
                try {
                    // 4.1 售罄熔断：检查 Redis 中的售罄标识，避免无效 SQL 执行
                    if (skuStock.isSkuStockZero(sku)) {
                        log.warn("【熔断】SKU: {} 已标记售罄，拦截异步更新请求 | 待更新数: {}", sku, count);
                        return;
                    }

                    // 4.2 批量持久化：通过单条 SQL 执行 stock = stock - count
                    // 注意：ShardingSphere 会自动处理这个 update 语句的分片路由
                    log.info("开始异步同步活动库存 | SKU: {} | 本轮合并更新量: {}", sku, count);
                    skuStock.updateActivitySkuStockBatch(sku, count);

                } catch (Exception e) {
                    // 单个 SKU 同步异常不中断整体任务
                    log.error("【错误】同步 SKU 物理库存异常 | SKU: {} | 累计数: {}", sku, count, e);
                }
            });

        } catch (Exception e) {
            log.error("活动 SKU 库存同步任务链路执行严重异常", e);
            // 抛出异常反馈给 XXL-JOB 管理后台记录状态
            throw e;
        } finally {
            // [步骤 5] 释放分布式锁
            if (isLocked) {
                lock.unlock();
                log.info(">>>>>> XXL-JOB 活动 SKU 库存同步任务执行完毕，锁已释放");
            }
        }
    }
}