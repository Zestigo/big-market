package com.c.trigger.job;

import com.c.domain.strategy.model.vo.StrategyAwardStockKeyVO;
import com.c.domain.strategy.service.IRaffleStock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 基础设施层：策略奖品库存异步同步任务
 * <p>
 * 职责：
 * 1. 最终一致性维护：将 Redis 中预扣减的逻辑库存状态，异步刷新至数据库物理表。
 * 2. 削峰填谷：通过批处理机制，缓解高并发秒杀时刻对数据库生成的 IOPS 压力。
 * 3. 错峰调度：通过合理的 Cron 表达式，避开整点任务高峰。
 *
 * @author cyh
 * @date 2026/01/20
 */
@Slf4j
@Component
public class UpdateStrategyAwardStockJob {

    @Resource
    private IRaffleStock raffleStock;

    /**
     * 执行库存同步任务
     * 调度策略：每 5 秒触发一次。
     * 1. 实时性保障：采用高频触发模式，缩短 Redis 与 DB 之间的数据差值。
     * 2. 消息驱动：从阻塞队列/延迟队列中提取待更新标识。
     * 3. 容错处理：单条记录更新失败不影响整体任务进度。
     */
    @Scheduled(cron = "0/5 * * * * ?")
    public void exec() {
        try {
            // [步骤 1] 循环提取待处理库存消息，直至队列清空
            while (true) {
                // 从领域层提供的消费接口获取待更新对象
                StrategyAwardStockKeyVO strategyAwardStockKeyVO = raffleStock.takeQueueValue();

                // 队列为空则退出当前任务周期
                if (null == strategyAwardStockKeyVO) {
                    break;
                }

                // [步骤 2] 执行数据库库存同步逻辑
                try {
                    log.info("开始同步奖品物理库存 | strategyId: {} | awardId: {}",
                            strategyAwardStockKeyVO.getStrategyId(),
                            strategyAwardStockKeyVO.getAwardId());

                    // 核心动作：更新数据库中 strategy_award 表的 surplus_count
                    raffleStock.updateStrategyAwardStock(
                            strategyAwardStockKeyVO.getStrategyId(),
                            strategyAwardStockKeyVO.getAwardId()
                    );

                } catch (Exception e) {
                    // 单条记录同步异常，记录日志并继续处理后续任务，防止任务中断
                    log.error("同步奖品物理库存失败 | strategyId: {} | awardId: {}",
                            strategyAwardStockKeyVO.getStrategyId(),
                            strategyAwardStockKeyVO.getAwardId(), e);
                }
            }
        } catch (Exception e) {
            log.error("策略奖品库存异步更新任务执行严重异常", e);
        }
    }
}