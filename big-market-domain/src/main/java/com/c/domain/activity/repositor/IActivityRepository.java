package com.c.domain.activity.repositor;

import com.c.domain.activity.model.aggregate.CreateOrderAggregate;
import com.c.domain.activity.model.entity.ActivityCountEntity;
import com.c.domain.activity.model.entity.ActivityEntity;
import com.c.domain.activity.model.entity.ActivitySkuEntity;
import com.c.domain.activity.model.vo.ActivitySkuStockKeyVO;

import java.util.Date;

/**
 * 抽奖活动仓储接口 (Activity Domain Repository)
 * 1. 领域隔离：作为适配器层，封装对数据库 (MySQL) 和缓存 (Redis) 的访问，屏蔽基础设施层的技术实现差异。
 * 2. 聚合根管理：维护 {@link CreateOrderAggregate} 聚合根的持久化，确保活动单生成与账户额度变更的原子性。
 * 3. 库存控制中心：定义从物理库库存预热、缓存预扣减到异步流水同步的全链路数据契约。
 *
 * @author cyh
 * @date 2026/01/27
 */
public interface IActivityRepository {

    /**
     * 查询活动 SKU 持久化详情
     * 业务背景：下单流程的第一步，用于获取 SKU 关联的 activityId、countId 以及当前的销售策略。
     *
     * @param sku 库存单元唯一标识（活动商品的最小销售单元）
     * @return ActivitySkuEntity 包含活动参与门槛及关联规则 ID 的领域实体
     */
    ActivitySkuEntity queryActivitySku(Long sku);

    /**
     * 查询抽奖活动主体配置
     * 业务背景：校验活动状态（开启/关闭）、活动有效期（起止时间）以及关联的策略 ID。
     *
     * @param activityId 活动唯一标识 ID
     * @return ActivityEntity 活动配置领域实体
     */
    ActivityEntity queryRaffleActivityByActivityId(Long activityId);

    /**
     * 查询抽奖额度限制规则
     * 业务背景：获取活动定义的次数限制，包括总限额、日限额和月限额配置。
     *
     * @param activityCountId 额度规则配置 ID
     * @return ActivityCountEntity 次数限制领域实体
     */
    ActivityCountEntity queryRaffleActivityCountByActivityCountId(Long activityCountId);

    /**
     * 持久化活动下单聚合根（核心事务操作）
     * * 业务逻辑：
     * 1. 记录活动参与订单 (raffle_activity_order)。
     * 2. 更新/新增用户活动账户额度 (raffle_activity_account)。
     * 3. 必须在同一个数据库事务内完成，确保“扣减次数”与“生成单据”的强一致性。
     *
     * @param createOrderAggregate 包含用户信息、活动信息及订单明细的聚合根对象
     */
    void doSaveOrder(CreateOrderAggregate createOrderAggregate);

    /**
     * 获取异步库存扣减流水（供 Worker 节点调用）
     * 设计意图：从分布式阻塞队列（如 Redis 延迟队列或 List）中弹出预扣成功的 SKU 记录，用于同步物理库。
     *
     * @return ActivitySkuStockKeyVO 包含待更新库存的 SKU 信息载体
     */
    ActivitySkuStockKeyVO takeQueueValue();

    /**
     * 清理库存同步队列
     * 场景说明：用于系统运维、库存对账异常后的数据重置。
     */
    void clearQueueValue();

    /**
     * 将预扣成功的 SKU 存入异步更新队列
     * * 核心价值：实现“写合并”与“削峰填谷”。
     * 将高并发下的瞬时库存更新压力，转化为队列中的有序流水，由后台 Job 平滑地同步至 MySQL。
     *
     * @param activitySkuStockKeyVO 成功扣减的库存流水 VO
     */
    void activitySkuStockConsumeSendQueue(ActivitySkuStockKeyVO activitySkuStockKeyVO);

    /**
     * 预热活动 SKU 缓存库存
     * * 执行时机：活动装配阶段 (Armory)。
     * 逻辑：将 MySQL 数据库中的 `stock_count_surplus` 剩余库存加载至 Redis AtomicLong 结构。
     *
     * @param cacheKey   Redis 存储的唯一键（建议格式：activity_sku_stock_count_{sku}）
     * @param stockCount 物理库当前的实时剩余库存数量
     */
    void cacheActivitySkuStockCount(String cacheKey, Integer stockCount);

    /**
     * 执行 Redis 原子预扣减库存（高并发控制）
     * <p>
     * 设计意图：利用 Redis 单线程原子性抗住第一波并发压力。
     *
     * @param sku         商品 SKU 编号
     * @param cacheKey    库存对应的 Redis Key
     * @param endDateTime 活动结束时间（用于设置 Key 过期时间，防止内存泄露）
     * @return boolean    扣减结果：true-成功（获得下单资格）；false-失败（库存不足或已售罄）
     */
    boolean subtractionActivitySkuStock(Long sku, String cacheKey, Date endDateTime);

    /**
     * 物理库存置零同步
     * 场景：当 Redis 判定库存售罄时，强行将数据库中对应的 `stock_count_surplus` 置为 0，防止数据回冲或超卖。
     *
     * @param sku 商品唯一标识 SKU
     */
    void zeroOutActivitySkuStock(Long sku);

    /**
     * 物理库存异步扣减（单条更新）
     *
     * @param sku 商品唯一标识 SKU
     */
    void subtractionActivitySkuStock(Long sku);

    /**
     * 物理库存批量同步更新
     * 场景：Job 消费队列时，将本周期内合并后的扣减量 `count` 一次性更新至数据库，提升 IO 效率。
     *
     * @param sku   商品唯一标识 SKU
     * @param count 本次需要扣减的累积库存增量
     */
    void updateActivitySkuStockBatch(Long sku, Integer count);

    /**
     * 设置 SKU 售罄标识位（防止缓存击穿）
     * * 业务意图：在 Redis 中标记该 SKU 已售罄。
     * 后续请求在进入预扣减逻辑前先检查此标识，避免无效的 DECR 操作。
     *
     * @param sku 商品唯一标识 SKU
     */
    void setSkuStockZeroFlag(Long sku);

    /**
     * 检查当前 SKU 是否已处于售罄熔断状态
     *
     * @param sku 商品唯一标识 SKU
     * @return true-已售罄，直接拦截请求；false-仍有库存
     */
    boolean isSkuStockZero(Long sku);
}