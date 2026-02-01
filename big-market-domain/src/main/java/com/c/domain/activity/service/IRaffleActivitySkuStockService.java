package com.c.domain.activity.service;

import com.c.domain.activity.model.vo.ActivitySkuStockKeyVO;

/**
 * 活动 SKU 库存异步处理服务接口
 * 1. 最终一致性保障：负责将 Redis 缓存中的预扣减结果，通过异步链路同步至 MySQL 持久层。
 * 2. 削峰填谷中间件：作为库存消耗队列（BlockingQueue/Stream）的调度核心，缓冲高并发下的瞬时数据库写压力。
 * 3. 售罄状态同步：管理“售罄标识位”，实现数据库与缓存层在库存枯竭时的状态强一致性。
 *
 * @author cyh
 * @date 2026/01/31
 */
public interface IRaffleActivitySkuStockService {

    /**
     * 获取库存消耗队列中的待处理元素
     * 业务场景：由后端异步 Worker 线程调用。当队列为空时进入阻塞状态，一旦有新的预扣减流水入队，立即唤醒执行同步任务。
     *
     * @return {@link ActivitySkuStockKeyVO} 封装了需要同步的 SKU 信息及策略上下文
     * @throws InterruptedException 当处理线程在阻塞等待期间被强制中断时抛出
     */
    ActivitySkuStockKeyVO takeQueueValue() throws InterruptedException;

    /**
     * 执行单条物理库存扣减（原子同步）
     * 业务逻辑：将缓存扣减成功的指令反映到数据库：
     * UPDATE activity_sku SET stock_surplus = stock_surplus - 1 WHERE sku = ?
     *
     * @param sku 活动商品 SKU 唯一标识
     */
    void subtractionActivitySkuStock(Long sku);

    /**
     * 物理库存强行置零（售罄熔断）
     * 业务逻辑：当 Redis 缓存层检测到库存绝对值为 0 或负数时，强行同步数据库库存为 0。
     * 意图：防止由于网络抖动或异步延迟导致的数据库剩余少量库存被误认为“仍可购买”的情况。
     *
     * @param sku 活动商品 SKU 唯一标识
     */
    void zeroOutActivitySkuStock(Long sku);

    /**
     * 批量合并物理库存更新
     * 设计意图：写合并 (Write Combining)。
     * 在高并发场景下，Job 会将同一周期内（如 5s 内）同一 SKU 的多次扣减请求累加为总数 `count`，
     * 仅发起一次数据库更新：UPDATE activity_sku SET stock_surplus = stock_surplus - :count WHERE sku = ?
     * 极大降低了数据库行锁的持有频率和 IOPS。
     *
     * @param sku   活动商品 SKU 唯一标识
     * @param count 本次累积需要同步扣减的库存总量
     */
    void updateActivitySkuStockBatch(Long sku, Integer count);

    /**
     * 标记 SKU 已售罄（分布式标识）
     * 业务意图：在分布式环境中设置一个快速失败 (Fail-Fast) 的标识位。
     * 一旦标记，后续的异步 Job 或校验逻辑可以跳过繁重的数据库操作，直接根据标识位拦截请求。
     *
     * @param sku 活动商品 SKU 唯一标识
     */
    void setSkuStockZeroFlag(Long sku);

    /**
     * 校验 SKU 是否已处于售罄熔断状态
     * 业务场景：在下单校验环节或 Job 批量更新环节进行前置检查，实现链路熔断，保护数据库。
     *
     * @param sku 活动商品 SKU 唯一标识
     * @return true-已售罄（熔断）；false-仍有库存或状态正常
     */
    boolean isSkuStockZero(Long sku);
}