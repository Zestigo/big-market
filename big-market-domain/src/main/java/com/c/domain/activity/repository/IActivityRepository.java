package com.c.domain.activity.repository;

import com.c.domain.activity.model.aggregate.CreatePartakeOrderAggregate;
import com.c.domain.activity.model.aggregate.CreateQuotaOrderAggregate;
import com.c.domain.activity.model.entity.*;
import com.c.domain.activity.model.vo.ActivitySkuStockKeyVO;

import java.util.Date;
import java.util.List;

/**
 * 抽奖活动仓储接口 (Activity Domain Repository)
 * 1. 领域隔离：封装对 MySQL 和 Redis 的访问，屏蔽基础设施层的技术实现差异。
 * 2. 聚合根管理：负责 {@link CreateQuotaOrderAggregate} 和 {@link CreatePartakeOrderAggregate} 的持久化。
 * 3. 库存控制中心：定义从缓存预扣减到数据库异步同步的全链路数据契约。
 *
 * @author cyh
 * @since 2026/01/27
 */
public interface IActivityRepository {

    /**
     * 查询活动 SKU 持久化详情
     * 用于下单流程第一步，获取 SKU 关联的活动 ID 及参与门槛。
     *
     * @param sku 库存单元唯一标识
     * @return {@link ActivitySkuEntity} 包含活动及规则配置的实体
     */
    ActivitySkuEntity queryActivitySku(Long sku);

    /**
     * 查询抽奖活动主体配置
     * 校验活动状态、有效期及关联的策略 ID。
     *
     * @param activityId 活动唯一标识
     * @return {@link ActivityEntity} 活动配置实体
     */
    ActivityEntity queryRaffleActivityByActivityId(Long activityId);

    /**
     * 查询抽奖额度限制规则
     * 获取总限额、日限额和月限额配置。
     *
     * @param activityCountId 额度规则配置 ID
     * @return {@link ActivityCountEntity} 次数限制实体
     */
    ActivityCountEntity queryRaffleActivityCountByActivityCountId(Long activityCountId);

    /**
     * 持久化活动下单聚合根（核心事务操作）
     * 包含记录活动参与订单与更新用户账户额度，必须在同一个数据库事务内完成。
     *
     * @param createQuotaOrderAggregate 活动下单聚合根对象
     */
    void doSaveOrder(CreateQuotaOrderAggregate createQuotaOrderAggregate);

    /**
     * 从分布式队列中提取库存扣减任务
     *
     * @return {@link ActivitySkuStockKeyVO} 待更新库存的 SKU 标识
     */
    ActivitySkuStockKeyVO takeQueueValue();

    /**
     * 清理库存同步队列（用于异常对账或数据重置）
     */
    void clearQueueValue();

    /**
     * 将预扣成功的库存流水存入异步更新队列
     * 实现削峰填谷，由后台 Job 批量更新 MySQL。
     *
     * @param activitySkuStockKeyVO 库存流水标识对象
     */
    void activitySkuStockConsumeSendQueue(ActivitySkuStockKeyVO activitySkuStockKeyVO);

    /**
     * 预热活动 SKU 缓存库存至 Redis
     *
     * @param cacheKey   Redis 存储 Key
     * @param stockCount 待加载的物理剩余库存
     */
    void cacheActivitySkuStockCount(String cacheKey, Integer stockCount);

    /**
     * 执行 Redis 原子预扣减库存
     * 利用 Redis 单线程原子性抗住瞬时并发压力。
     *
     * @param sku         商品 SKU 编号
     * @param cacheKey    Redis 库存 Key
     * @param endDateTime 活动结束时间（用于 Key 过期时间控制）
     * @return boolean true-扣减成功且有余量；false-库存不足
     */
    boolean subtractionActivitySkuStock(Long sku, String cacheKey, Date endDateTime);

    /**
     * 物理库存售罄置零同步
     * 缓存判定售罄后，强行将 DB 剩余库存置为 0。
     *
     * @param sku 商品 SKU 编号
     */
    void zeroOutActivitySkuStock(Long sku);

    /**
     * 物理库存异步扣减（单条更新）
     *
     * @param sku 商品 SKU 编号
     */
    void subtractionActivitySkuStock(Long sku);

    /**
     * 物理库存批量同步更新
     *
     * @param sku   商品 SKU 编号
     * @param count 本次累积需要扣减的增量值
     */
    void updateActivitySkuStockBatch(Long sku, Integer count);

    /**
     * 设置 SKU 售罄标识位（用于 Redis 快速熔断）
     *
     * @param sku 商品 SKU 编号
     */
    void setSkuStockZeroFlag(Long sku);

    /**
     * 检查当前 SKU 是否已处于售罄状态
     *
     * @param sku 商品 SKU 编号
     * @return true-已售罄；false-仍有余量
     */
    boolean isSkuStockZero(Long sku);

    /**
     * 查询用户是否存在【已创建但未使用】的活动参与订单
     * 用于幂等性控制，防止由于重试导致的用户额度超额扣减。
     *
     * @param partakeRaffleActivityEntity 参与信息实体（包含 userId, activityId）
     * @return {@link UserRaffleOrderEntity} 已存在订单，若无则返回 null
     */
    UserRaffleOrderEntity queryNoUsedRaffleOrder(PartakeRaffleActivityEntity partakeRaffleActivityEntity);

    /**
     * 查询用户在特定活动下的【总账户】额度
     *
     * @param userId     用户 ID
     * @param activityId 活动 ID
     * @return {@link ActivityAccountEntity} 账户总额度实体
     */
    ActivityAccountEntity queryActivityAccountByUserId(String userId, Long activityId);

    /**
     * 查询用户在特定活动下的【月账户】额度快照
     *
     * @param userId     用户 ID
     * @param activityId 活动 ID
     * @param month      月份标识（如 2026-02）
     * @return {@link ActivityAccountMonthEntity} 月度额度实体
     */
    ActivityAccountMonthEntity queryActivityAccountMonthByUserId(String userId, Long activityId, String month);

    /**
     * 查询用户在特定活动下的【日账户】额度快照
     *
     * @param userId     用户 ID
     * @param activityId 活动 ID
     * @param day        日期标识（如 2026-02-04）
     * @return {@link ActivityAccountDayEntity} 日度额度实体
     */
    ActivityAccountDayEntity queryActivityAccountDayByUserId(String userId, Long activityId, String day);

    /**
     * 持久化活动参与订单聚合根
     * 核心原子操作：1.更新多级账户额度；2.保存参与订单；3.写入 Task 任务。
     *
     * @param createPartakeOrderAggregate 活动参与聚合根
     */
    void saveCreatePartakeOrderAggregate(CreatePartakeOrderAggregate createPartakeOrderAggregate);

    /**
     * 根据活动 ID 检索所有关联的 SKU 列表
     *
     * @param activityId 活动 ID
     * @return {@link List<ActivitySkuEntity>} SKU 实体集合
     */
    List<ActivitySkuEntity> queryActivitySkuListByActivityId(Long activityId);

    /**
     * 查询用户当日累计已参与抽奖活动的次数
     * 用于前端展示或后端 RuleLock 阈值校验。
     *
     * @param activityId 活动 ID
     * @param userId     用户 ID
     * @return 当日已参与次数
     */
    Integer queryRaffleActivityAccountDayPartakeCount(Long activityId, String userId);

    /**
     * 查询用户活动账户额度实体
     * 获取用户在指定活动下的账户记录，包含总次数、日次数及月次数的配额详情。
     *
     * @param activityId 活动ID
     * @param userId     用户唯一ID
     * @return 活动账户额度实体
     */
    ActivityAccountEntity queryActivityAccountEntity(Long activityId, String userId);

    /**
     * 查询用户在特定活动下的已参与抽奖次数
     * 逻辑：已参与次数 = 总配额 - 剩余配额
     *
     * @param activityId 活动ID
     * @param userId     用户ID
     * @return 已参与抽奖的总次数
     */
    Integer queryRaffleActivityAccountPartakeCount(Long activityId, String userId);

}