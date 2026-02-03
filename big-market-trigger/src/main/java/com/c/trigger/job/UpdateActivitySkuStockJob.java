package com.c.trigger.job;

import com.c.domain.activity.model.vo.ActivitySkuStockKeyVO;
import com.c.domain.activity.service.IRaffleActivitySkuStockService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.Resource;

/**
 * 活动 SKU 物理库存异步同步任务
 * 职责：
 * 1. 异步削峰：消费 Redis 预扣减产生的库存流水队列，减轻数据库高频行锁竞争。
 * 2. 批量写合并：将同一周期内的多次扣减请求合并为单次数据库更新，大幅提升 IOPS。
 * 3. 最终一致性保障：通过分布式标识位拦截已售罄 SKU，确保无效扣减不再下发至持久层。
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
     * 调度策略：每 5 秒触发一次（0, 5, 10, 15...）。
     * 错峰设计：与奖品库存同步任务（15s 周期）时间节点错开，平滑数据库连接负载。
     */
    @Scheduled(cron = "0 */1 * * * ?")
    public void exec() {
        try {
            // 1. 本地内存合并容器：Key 为 SKU 标识，Value 为本周期内累计需要扣减的库存数量
            Map<Long, Integer> skuCountMap = new HashMap<>();

            // 2. 队列预取：非阻塞式提取当前待处理的所有库存扣减指令
            while (true) {
                ActivitySkuStockKeyVO vo = skuStock.takeQueueValue();
                if (vo == null) break;
                // 累计同一 SKU 的消耗量，实现写合并逻辑
                skuCountMap.merge(vo.getSku(), 1, Integer::sum);
            }

            // 本周期无业务数据则直接退出
            if (skuCountMap.isEmpty()) return;

            // 3. 批量更新执行：遍历合并后的 SKU 集合进行持久化操作
            skuCountMap.forEach((sku, count) -> {
                try {
                    // 【熔断拦截】检查售罄标识位。若实时监听器（如 Canal 或实时消费）已将数据库置零，
                    // 则跳过此 SKU 的异步扣减，避免数据库库存出现负数或无效更新。
                    if (skuStock.isSkuStockZero(sku)) {
                        log.info("【Job】SKU:{} 存在售罄熔断标识，拦截无效异步更新请求", sku);
                        return;
                    }

                    // 执行批量库存扣减更新：UPDATE table SET stock = stock - count WHERE sku = ?
                    skuStock.updateActivitySkuStockBatch(sku, count);
                } catch (Exception e) {
                    log.error("【Job】同步 SKU 物理库存失败 sku:{} count:{}", sku, count, e);
                }
            });

        } catch (Exception e) {
            log.error("【Job】库存同步链路执行异常", e);
        }
    }
}