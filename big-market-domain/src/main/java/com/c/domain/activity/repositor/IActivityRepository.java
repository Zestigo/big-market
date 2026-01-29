package com.c.domain.activity.repositor;

import com.c.domain.activity.model.aggregate.CreateOrderAggregate;
import com.c.domain.activity.model.entity.ActivityCountEntity;
import com.c.domain.activity.model.entity.ActivityEntity;
import com.c.domain.activity.model.entity.ActivitySkuEntity;
import com.c.domain.activity.model.vo.ActivitySkuStockKeyVO;

import java.util.Date;

/**
 * @author cyh
 * @description 抽奖活动仓储接口
 * 职责：
 * 1. 领域层与基础层解耦：定义数据操作契约，不关心底层是 MySQL、Redis 还是 MQ。
 * 2. 维护领域对象：处理聚合根（Aggregate）的整体持久化，确保业务事务的完整性。
 * @date 2026/01/27
 */
public interface IActivityRepository {

    /**
     * 查询活动 SKU 详情
     * 用于获取活动商品的基础配置，是下单流程的入口数据。
     *
     * @param sku 库存单元唯一标识
     */
    ActivitySkuEntity queryActivitySku(Long sku);

    /**
     * 查询抽奖活动配置
     *
     * @param activityId 活动唯一标识
     */
    ActivityEntity queryRaffleActivityByActivityId(Long activityId);

    /**
     * 查询抽奖次数限制规则
     *
     * @param activityCountId 次数规则配置 ID
     */
    ActivityCountEntity queryRaffleActivityCountByActivityCountId(Long activityCountId);

    /**
     * 保存下单聚合根对象
     * 这是一个原子操作，通常包含：写入活动单、更新用户账户（次数）、以及扣减缓存后的日志记录。
     *
     * @param createOrderAggregate 包含订单详情、账户信息、行为状态的聚合根
     */
    void doSaveOrder(CreateOrderAggregate createOrderAggregate);

    /**
     * 从预扣库存队列中获取 SKU 消费记录
     * 场景：用于异步同步数据库库存，通常从 Redis 的 List 或 BlockingQueue 中弹出数据。
     *
     * @return ActivitySkuStockKeyVO 包含需要同步的 SKU 信息
     */
    ActivitySkuStockKeyVO takeQueueValue();

    /**
     * 清空预扣库存队列数据
     * 用于系统重置或异常处理时清空暂存的消息。
     */
    void clearQueueValue();

    /**
     * 异步更新数据库中的 SKU 库存
     * 场景：根据 Redis 的预扣减结果，定时或触发式地同步回写数据库。
     *
     * @param sku 商品唯一标识
     */
    void updateActivitySkuStock(Long sku);

    /**
     * 将扣减成功的 SKU 加入异步更新队列
     * 实现削峰填谷的关键，将即时的请求压力转化为后台的顺序处理。
     *
     * @param activitySkuStockKeyVO 库存扣减信息载体
     */
    void activitySkuStockConsumeSendQueue(ActivitySkuStockKeyVO activitySkuStockKeyVO);

    /**
     * 强制清空/同步数据库库存为 0
     * 场景：当缓存中库存完全耗尽且发送售罄事件时，确保 DB 状态与缓存绝对一致。
     *
     * @param sku 商品唯一标识
     */
    void clearActivitySkuStock(Long sku);

    /**
     * 缓存活动 SKU 库存数量（库存预热/初始化）
     * 在活动装配阶段（Armory），将数据库中的物理库存同步至 Redis 缓存中，为高并发扣减做准备。
     *
     * @param cacheKey   Redis 存储的唯一键（通常格式为：activity_sku_stock_count_{sku}）
     * @param stockCount 需要初始化的库存总数（来自数据库的 raffle_activity_sku.stock_count）
     */
    void cacheActivitySkuStockCount(String cacheKey, Integer stockCount);

    /**
     * 原子扣减活动 SKU 库存（Redis 高并发预扣减）
     *
     * @param sku         商品 SKU 编号，用于识别具体的抽奖商品单元。
     * @param cacheKey    库存对应的 Redis Key。
     * @param endDateTime 活动结束时间。用于控制缓存的生命周期，确保活动结束后相关库存 Key 能够自动过期或被回收。
     * @return boolean    扣减结果反馈：
     * - true：扣减成功，代表当前缓存库存充裕，用户可以继续抽奖下单。
     * - false：扣减失败，代表库存已耗尽（售罄）或当前 SKU 不可用。
     */
    boolean subtractionActivitySkuStock(Long sku, String cacheKey, Date endDateTime);
}