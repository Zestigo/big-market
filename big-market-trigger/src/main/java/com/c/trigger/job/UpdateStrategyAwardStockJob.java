package com.c.trigger.job;

import com.c.domain.strategy.model.vo.StrategyAwardStockKeyVO;
import com.c.domain.strategy.service.IRaffleStock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 策略奖品库存异步更新定时任务
 * 职责：
 * 1. 异步同步：消费奖品库存消耗队列，将 Redis 预扣减后的库存状态同步至数据库持久层。
 * 2. 削峰填谷：通过批处理机制减轻数据库在高并发抽奖场景下的直接写入压力。
 *
 * @author cyh
 * @date 2026/01/20
 */
@Slf4j
@Component()
public class UpdateStrategyAwardStockJob {

    @Resource
    private IRaffleStock raffleStock;

    /**
     * 执行库存同步任务
     * 调度策略：每分钟从第 2 秒开始，每 15 秒触发一次（2, 17, 32, 47）。
     * 优化点：通过时间偏移，与活动 SKU 库存任务错峰执行，避免数据库连接瞬时竞争。
     */
    @Scheduled(cron = "0 */1 * * * ?")
    public void exec() {
        try {
            // 1. 临时容器：用于记录本轮扫描到的待处理奖品，实现去重与元数据暂存
            Set<String> distinctKeys = new HashSet<>();
            Map<String, StrategyAwardStockKeyVO> dataMap = new HashMap<>();

            // 2. 循环提取队列：非阻塞式获取当前所有待处理的消息
            while (true) {
                StrategyAwardStockKeyVO vo = raffleStock.takeQueueValue();
                if (vo == null) break; // 队列为空则结束提取

                // 组合唯一键（策略ID + 奖品ID），确保同奖品在本轮任务中仅触发一次数据库写操作
                String key = vo.getStrategyId() + "_" + vo.getAwardId();
                distinctKeys.add(key);
                dataMap.putIfAbsent(key, vo);
            }

            // 无任务直接返回，节省日志与计算资源
            if (distinctKeys.isEmpty()) return;

            // 3. 幂等批量处理：遍历去重后的奖品列表，同步物理库存
            for (String key : distinctKeys) {
                StrategyAwardStockKeyVO vo = dataMap.get(key);
                try {
                    log.info("定时任务启动：同步策略奖品物理库存 strategyId:{}, awardId:{}", vo.getStrategyId(),
                            vo.getAwardId());
                    // 核心动作：调用领域服务，将预扣产生的消耗记录同步至数据库
                    raffleStock.updateStrategyAwardStock(vo.getStrategyId(), vo.getAwardId());
                } catch (Exception e) {
                    log.error("定时任务异常：同步策略奖品库存失败 strategyId:{}", vo.getStrategyId(), e);
                }
            }
        } catch (Exception e) {
            log.error("定时任务严重异常：奖品库存处理链路中断", e);
        }
    }
}