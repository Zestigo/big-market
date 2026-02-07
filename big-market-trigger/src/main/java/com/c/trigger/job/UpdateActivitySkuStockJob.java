package com.c.trigger.job;

import com.c.domain.activity.model.vo.ActivitySkuStockKeyVO;
import com.c.domain.activity.service.IRaffleActivitySkuStockService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * 基础设施层：活动 SKU 物理库存异步同步任务
 * 1. 异步削峰：通过消费 Redis 扣减产生的延迟队列，平摊高并发下的数据库压力。
 * 2. 批量写合并：在同一调度周期内，将多次单量扣减合并为一次批量更新 SQL，极大减少行锁竞争。
 * 3. 错峰调度：通过独立频率设置，避免与奖品库存同步任务发生 I/O 冲突。
 *
 * @author cyh
 * @date 2026/01/28
 */
@Slf4j
@Component
public class UpdateActivitySkuStockJob {

    @Resource
    private IRaffleActivitySkuStockService skuStock;

    /**
     * 执行库存同步任务
     * 运行频率：每 5 秒触发一次。
     * 1. 累计计算：利用 Map 在内存中汇总本周期内各 SKU 的消耗总数。
     * 2. 批量扣减：将累计值通过单条 SQL 更新至数据库：UPDATE table SET stock = stock - :count
     * 3. 故障容错：单个 SKU 更新失败不阻塞后续 SKU 的同步。
     */
    @Scheduled(cron = "0/5 * * * * ?")
    public void exec() {
        try {
            // [步骤 1] 内存合并容器：Key = sku, Value = 本周期累计消耗量
            Map<Long, Integer> skuCountMap = new HashMap<>();

            // [步骤 2] 非阻塞提取队列消息，直至清空当前待处理指令
            while (true) {
                ActivitySkuStockKeyVO vo = skuStock.takeQueueValue();
                if (null == vo) {
                    break;
                }
                // 使用 merge 算子实现写合并逻辑，统计本轮消耗总数
                skuCountMap.merge(vo.getSku(), 1, Integer::sum);
            }

            // 无任务则跳过执行
            if (skuCountMap.isEmpty()) {
                return;
            }

            // [步骤 3] 遍历合并后的集合，执行持久化更新
            skuCountMap.forEach((sku, count) -> {
                try {
                    // 3.1 熔断拦截：检查售罄标识位，避免无效 SQL 执行
                    if (skuStock.isSkuStockZero(sku)) {
                        log.warn("【熔断】SKU: {} 已标记售罄，拦截异步更新请求 | 待更新数: {}", sku, count);
                        return;
                    }

                    // 3.2 批量异步更新数据库库存
                    log.info("开始异步同步活动库存 | SKU: {} | 累计消耗量: {}", sku, count);
                    skuStock.updateActivitySkuStockBatch(sku, count);

                } catch (Exception e) {
                    log.error("【错误】同步活动 SKU 物理库存异常 | SKU: {} | 累计数: {}", sku, count, e);
                }
            });

        } catch (Exception e) {
            log.error("活动 SKU 库存同步链路执行严重异常", e);
        }
    }
}