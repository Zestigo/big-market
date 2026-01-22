package com.c.trigger.job;

import com.c.domain.strategy.model.vo.StrategyAwardStockKeyVO;
import com.c.domain.strategy.service.IRaffleStock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 更新奖项库存
 *
 * @author cyh
 * @date 2026/01/20
 */
@Slf4j
@Component()
public class UpdateAwardStockJob {
    @Resource
    private IRaffleStock raffleStock;

    @Scheduled(cron = "0/5 * * * * ?")
    public void exec() {
        try {
            log.info("定时任务，更新奖品消耗库存【延迟队列获取，降低对数据库的更新频次，不要产生竞争】");
            StrategyAwardStockKeyVO strategyAwardStockKeyVO = raffleStock.takeQueueValue();
            if (strategyAwardStockKeyVO == null) return;
            Long strategyId = strategyAwardStockKeyVO.getStrategyId();
            Integer awardId = strategyAwardStockKeyVO.getAwardId();
            log.info("定时任务，更新奖品消耗库存 strategyId:{}, awardId:{}", strategyId, awardId);
            raffleStock.updateStrategyAwardStock(strategyId, awardId);
        } catch (Exception e) {
            log.error("定时任务，更新奖品消耗库存失败", e);
        }
    }
}
