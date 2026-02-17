package com.c.domain.activity.repository;

import com.c.domain.activity.model.aggregate.CreatePartakeOrderAggregate;
import com.c.domain.activity.model.aggregate.CreateQuotaOrderAggregate;
import com.c.domain.activity.model.entity.*;
import com.c.domain.activity.model.vo.ActivitySkuStockKeyVO;

import java.util.Date;
import java.util.List;

/**
 * 抽奖活动仓储接口
 * 负责抽奖活动领域内聚合根的持久化及缓存与数据库的数据同步契约。
 *
 * @author cyh
 * @since 2026/01/27
 */
public interface IActivityRepository {

    /**
     * 保存无需支付的额度单
     * 包含记录活动参与订单与更新用户账户额度。
     *
     * @param createQuotaOrderAggregate 额度下单聚合根
     */
    void doSaveNoPayOrder(CreateQuotaOrderAggregate createQuotaOrderAggregate);

    /**
     * 保存积分支付的额度单
     *
     * @param createQuotaOrderAggregate 额度下单聚合根
     */
    void doSaveCreditPayOrder(CreateQuotaOrderAggregate createQuotaOrderAggregate);

    /**
     * 更新活动订单状态
     * 根据投递单信息修改订单状态，并确保业务流水幂等处理。
     *
     * @param deliveryOrderEntity 投递单实体
     */
    void updateOrder(DeliveryOrderEntity deliveryOrderEntity);

    /**
     * 查询活动 SKU 详情
     *
     * @param sku 库存单元唯一标识
     * @return 活动及规则配置实体
     */
    ActivitySkuEntity queryActivitySku(Long sku);

    /**
     * 查询抽奖活动配置
     *
     * @param activityId 活动唯一标识
     * @return 活动配置实体
     */
    ActivityEntity queryRaffleActivityByActivityId(Long activityId);

    /**
     * 查询抽奖额度限制规则
     *
     * @param activityCountId 额度规则配置 ID
     * @return 次数限制实体
     */
    ActivityCountEntity queryRaffleActivityCountByActivityCountId(Long activityCountId);

    /**
     * 获取库存同步队列值
     *
     * @return 待更新库存的 SKU 标识
     */
    ActivitySkuStockKeyVO takeQueueValue();

    /**
     * 清理库存同步队列
     */
    void clearQueueValue();

    /**
     * 发送库存扣减流水至延迟队列
     *
     * @param activitySkuStockKeyVO 库存流水标识对象
     */
    void activitySkuStockConsumeSendQueue(ActivitySkuStockKeyVO activitySkuStockKeyVO);

    /**
     * 预热活动 SKU 缓存库存
     *
     * @param cacheKey   缓存 Key
     * @param stockCount 物理剩余库存
     */
    void cacheActivitySkuStockCount(String cacheKey, Integer stockCount);

    /**
     * 执行原子预扣减库存
     *
     * @param sku         商品 SKU 编号
     * @param cacheKey    缓存库存 Key
     * @param endDateTime 活动结束时间
     * @return true-成功；false-失败/库存不足
     */
    boolean subtractionActivitySkuStock(Long sku, String cacheKey, Date endDateTime);

    /**
     * 物理库存售罄置零同步
     *
     * @param sku 商品 SKU 编号
     */
    void zeroOutActivitySkuStock(Long sku);

    /**
     * 物理库存异步扣减
     *
     * @param sku 商品 SKU 编号
     */
    void subtractionActivitySkuStock(Long sku);

    /**
     * 物理库存批量同步更新
     *
     * @param sku   商品 SKU 编号
     * @param count 累积扣减增量值
     */
    void updateActivitySkuStockBatch(Long sku, Integer count);

    /**
     * 设置 SKU 售罄标识
     *
     * @param sku 商品 SKU 编号
     */
    void setSkuStockZeroFlag(Long sku);

    /**
     * 检查 SKU 是否售罄
     *
     * @param sku 商品 SKU 编号
     * @return true-已售罄；false-仍有余量
     */
    boolean isSkuStockZero(Long sku);

    /**
     * 查询未使用的活动参与订单
     *
     * @param partakeRaffleActivityEntity 参与信息实体
     * @return 已存在订单流水
     */
    UserRaffleOrderEntity queryNoUsedRaffleOrder(PartakeRaffleActivityEntity partakeRaffleActivityEntity);

    /**
     * 查询用户活动账户总额度
     *
     * @param userId     用户 ID
     * @param activityId 活动 ID
     * @return 账户总额度实体
     */
    ActivityAccountEntity queryActivityAccountByUserId(String userId, Long activityId);

    /**
     * 查询用户活动账户月额度
     *
     * @param userId     用户 ID
     * @param activityId 活动 ID
     * @param month      月份标识
     * @return 月度额度实体
     */
    ActivityAccountMonthEntity queryActivityAccountMonthByUserId(String userId, Long activityId, String month);

    /**
     * 查询用户活动账户日额度
     *
     * @param userId     用户 ID
     * @param activityId 活动 ID
     * @param day        日期标识
     * @return 日度额度实体
     */
    ActivityAccountDayEntity queryActivityAccountDayByUserId(String userId, Long activityId, String day);

    /**
     * 保存活动参与订单聚合根
     * 包含更新多级额度账户、保存参与订单及写入任务流水。
     *
     * @param createPartakeOrderAggregate 活动参与聚合根
     */
    void saveCreatePartakeOrderAggregate(CreatePartakeOrderAggregate createPartakeOrderAggregate);

    /**
     * 查询活动关联的 SKU 列表
     *
     * @param activityId 活动 ID
     * @return SKU 实体集合
     */
    List<ActivitySkuEntity> queryActivitySkuListByActivityId(Long activityId);

    /**
     * 查询用户当日累计参与次数
     *
     * @param activityId 活动 ID
     * @param userId     用户 ID
     * @return 当日已参与次数
     */
    Integer queryRaffleActivityAccountDayPartakeCount(Long activityId, String userId);

    /**
     * 查询用户活动账户实体
     *
     * @param activityId 活动 ID
     * @param userId     用户 ID
     * @return 活动账户实体
     */
    ActivityAccountEntity queryActivityAccountEntity(Long activityId, String userId);

    /**
     * 查询用户活动已参与抽奖次数
     *
     * @param activityId 活动 ID
     * @param userId     用户 ID
     * @return 已参与抽奖总次数
     */
    Integer queryRaffleActivityAccountPartakeCount(Long activityId, String userId);

    /**
     * 查询用户指定 SKU 下的待支付订单记录
     *
     * @param skuRechargeEntity 包含用户ID和SKU信息的充值实体
     * @return 未支付订单业务实体
     */
    UnpaidActivityOrderEntity queryUnpaidActivityOrder(SkuRechargeEntity skuRechargeEntity);

    /**
     * 根据活动 ID 查询 SKU 产品实体列表
     *
     * @param activityId 活动唯一标识 ID
     * @return 包含 SKU 信息及次数配置的实体列表集合
     */
    List<SkuProductEntity> querySkuProductEntityListByActivityId(Long activityId);
}