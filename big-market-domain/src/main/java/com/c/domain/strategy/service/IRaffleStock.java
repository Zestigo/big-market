package com.c.domain.strategy.service;

import com.c.domain.strategy.model.vo.StrategyAwardStockKeyVO;

/**
 * 抽奖库存处理接口
 * * 职责：
 * 负责抽奖过程中库存的异步消费与数据库同步。
 * 通常与消息队列或延迟任务配合使用，实现缓存扣减后的最终一致性处理。
 *
 * @author cyh
 * @date 2026/01/20
 */
public interface IRaffleStock {

    /**
     * 从库存消耗队列中获取待处理任务
     * * 业务场景：
     * 当 Redis 预扣减库存成功后，任务会被推送至队列。本方法用于消费这些任务。
     * 该方法通常会阻塞当前线程，直到队列中有可用的数据。
     *
     * @return 包含策略ID和奖品ID的库存对象
     * @throws InterruptedException 当线程在阻塞等待时被中断抛出
     */
    StrategyAwardStockKeyVO takeQueueValue() throws InterruptedException;

    /**
     * 更新数据库中的奖品库存记录
     * * 业务逻辑：
     * 1. 接收来自队列的消费请求。
     * 2. 将 Redis 的预扣减结果同步到数据库，确保数据最终一致性。
     * 3. 建议采用行级锁或数据库原子更新：update ... set stock = stock - 1 where ...
     *
     * @param strategyId 策略ID
     * @param awardId    奖品ID
     */
    void updateStrategyAwardStock(Long strategyId, Integer awardId);
}