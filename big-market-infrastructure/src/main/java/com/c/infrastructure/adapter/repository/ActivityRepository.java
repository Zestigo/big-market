package com.c.infrastructure.adapter.repository;

import com.c.domain.activity.event.ActivitySkuStockZeroMessageEvent;
import com.c.domain.activity.model.aggregate.CreatePartakeOrderAggregate;
import com.c.domain.activity.model.aggregate.CreateQuotaOrderAggregate;
import com.c.domain.activity.model.entity.*;
import com.c.domain.activity.model.vo.ActivitySkuStockKeyVO;
import com.c.domain.activity.model.vo.ActivityStateVO;
import com.c.domain.activity.model.vo.UserRaffleOrderStateVO;
import com.c.domain.activity.repository.IActivityRepository;
import com.c.infrastructure.dao.*;
import com.c.infrastructure.event.EventPublisher;
import com.c.infrastructure.po.*;
import com.c.infrastructure.redis.IRedisService;
import com.c.types.common.Constants;
import com.c.types.enums.ResponseCode;
import com.c.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 活动领域仓储实现类
 * 职责：负责活动数据的持久化逻辑编排、多级缓存管理及高并发扣减库存的原子性保障。
 */
@Slf4j
@Repository
public class ActivityRepository implements IActivityRepository {

    @Resource
    private IRedisService redisService;
    @Resource
    private IRaffleActivityDao raffleActivityDao;
    @Resource
    private IRaffleActivitySkuDao raffleActivitySkuDao;
    @Resource
    private IRaffleActivityCountDao raffleActivityCountDao;
    @Resource
    private IRaffleActivityOrderDao raffleActivityOrderDao;
    @Resource
    private IRaffleActivityAccountDao raffleActivityAccountDao;
    @Resource
    private IRaffleActivityAccountMonthDao raffleActivityAccountMonthDao;
    @Resource
    private IRaffleActivityAccountDayDao raffleActivityAccountDayDao;
    @Resource
    private IUserRaffleOrderDao userRaffleOrderDao;
    @Resource
    private TransactionTemplate transactionTemplate;
    @Resource
    private ActivitySkuStockZeroMessageEvent activitySkuStockZeroMessageEvent;
    @Resource
    private EventPublisher eventPublisher;

    @Override
    public ActivitySkuEntity queryActivitySku(Long sku) {
        // 1. 优先查询数据库
        RaffleActivitySku raffleActivitySKU = raffleActivitySkuDao.queryActivitySku(sku);
        if (null == raffleActivitySKU) {
            throw new AppException(ResponseCode.ACTIVITY_NOT_EXIST.getCode(), "指定的SKU编号不存在");
        }

        String cacheKey = Constants.RedisKey.ACTIVITY_SKU_STOCK_COUNT_KEY + sku;

        // 2. 获取实时库存
        // 注意：如果 getValue 内部做了反序列化，这里可能也会报错，需确保 redisService 兼容
        Integer cacheSkuStock = redisService.getValue(cacheKey);

        // 3. 【核心修复】缓存补偿逻辑
        if (null == cacheSkuStock) {
            cacheSkuStock = raffleActivitySKU.getStockCountSurplus();

            /** * 💡 注意点：
             * 不要直接调用通用的 setValue(key, Object)，因为它会走 JSON 序列化。
             * 应该调用专门设置原子长整型的方法，确保 Redis 里存的是纯数字（Plain Text）。
             * 如果你的 redisService 没封装，可以考虑直接用 setAtomicLong 之类的方法。
             */
            redisService.setAtomicLong(cacheKey, cacheSkuStock);
        }

        return ActivitySkuEntity
                .builder()
                .sku(raffleActivitySKU.getSku())
                .activityId(raffleActivitySKU.getActivityId())
                .activityCountId(raffleActivitySKU.getActivityCountId())
                .stockCount(raffleActivitySKU.getStockCount())
                .stockCountSurplus(cacheSkuStock)
                .build();
    }

    @Override
    public ActivityEntity queryRaffleActivityByActivityId(Long activityId) {
        String cacheKey = Constants.RedisKey.ACTIVITY_KEY + activityId;
        ActivityEntity activityEntity = redisService.getValue(cacheKey);
        if (null != activityEntity) return activityEntity;

        RaffleActivity raffleActivity = raffleActivityDao.queryRaffleActivityByActivityId(activityId);
        if (null == raffleActivity) return null;

        activityEntity = ActivityEntity
                .builder()
                .activityId(raffleActivity.getActivityId())
                .activityName(raffleActivity.getActivityName())
                .activityDesc(raffleActivity.getActivityDesc())
                .beginDateTime(raffleActivity.getBeginDateTime())
                .endDateTime(raffleActivity.getEndDateTime())
                .strategyId(raffleActivity.getStrategyId())
                .state(ActivityStateVO.fromCode(raffleActivity.getState()))
                .build();

        redisService.setValue(cacheKey, activityEntity);
        return activityEntity;
    }

    @Override
    public ActivityCountEntity queryRaffleActivityCountByActivityCountId(Long activityCountId) {
        String cacheKey = Constants.RedisKey.ACTIVITY_COUNT_KEY + activityCountId;
        ActivityCountEntity activityCountEntity = redisService.getValue(cacheKey);
        if (null != activityCountEntity) return activityCountEntity;

        RaffleActivityCount raffleActivityCount =
                raffleActivityCountDao.queryRaffleActivityCountByActivityCountId(activityCountId);
        if (null == raffleActivityCount) return null;

        activityCountEntity = ActivityCountEntity
                .builder()
                .activityCountId(raffleActivityCount.getActivityCountId())
                .totalCount(raffleActivityCount.getTotalCount())
                .dayCount(raffleActivityCount.getDayCount())
                .monthCount(raffleActivityCount.getMonthCount())
                .build();

        redisService.setValue(cacheKey, activityCountEntity);
        return activityCountEntity;
    }

    @Override
    public void doSaveOrder(CreateQuotaOrderAggregate aggregate) {
        // 1. 数据准备
        ActivityOrderEntity orderEntity = aggregate.getActivityOrderEntity();

        // 订单流水 PO
        RaffleActivityOrder raffleActivityOrder = RaffleActivityOrder
                .builder()
                .userId(aggregate.getUserId())
                .sku(orderEntity.getSku())
                .activityId(orderEntity.getActivityId())
                .activityName(orderEntity.getActivityName())
                .strategyId(orderEntity.getStrategyId())
                .orderId(orderEntity.getOrderId())
                .orderTime(orderEntity.getOrderTime())
                .totalCount(orderEntity.getTotalCount())
                .dayCount(orderEntity.getDayCount())
                .monthCount(orderEntity.getMonthCount())
                .state(orderEntity
                        .getState()
                        .getCode())
                .outBusinessNo(orderEntity.getOutBusinessNo())
                .build();

        // 账户额度数据
        RaffleActivityAccount account = RaffleActivityAccount
                .builder()
                .userId(aggregate.getUserId())
                .activityId(aggregate.getActivityId())
                .totalCount(aggregate.getTotalCount())
                .totalCountSurplus(aggregate.getTotalCount())
                .dayCount(aggregate.getDayCount())
                .dayCountSurplus(aggregate.getDayCount())
                .monthCount(aggregate.getMonthCount())
                .monthCountSurplus(aggregate.getMonthCount())
                .build();

        // 2. 事务编排：利用数据库行锁与唯一索引代替分布式锁
        transactionTemplate.execute(status -> {
            try {
                // 步骤 1：插入订单记录（利用 out_business_no 唯一索引防重）
                raffleActivityOrderDao.insert(raffleActivityOrder);

                // 步骤 2：原子更新总账户（不存在则插入，存在则累加额度）
                raffleActivityAccountDao.upsertAddAccountQuota(account);

                // 步骤 3：原子更新月账户
                RaffleActivityAccountMonth monthPO = RaffleActivityAccountMonth
                        .builder()
                        .userId(aggregate.getUserId())
                        .activityId(aggregate.getActivityId())
                        .month(RaffleActivityAccountMonth.currentMonth())
                        .monthCount(aggregate.getMonthCount())
                        .monthCountSurplus(aggregate.getMonthCount())
                        .build();
                raffleActivityAccountMonthDao.upsertAddAccountQuota(monthPO);

                // 步骤 4：原子更新日账户
                RaffleActivityAccountDay dayPO = RaffleActivityAccountDay
                        .builder()
                        .userId(aggregate.getUserId())
                        .activityId(aggregate.getActivityId())
                        .day(RaffleActivityAccountDay.currentDay())
                        .dayCount(aggregate.getDayCount())
                        .dayCountSurplus(aggregate.getDayCount())
                        .build();
                raffleActivityAccountDayDao.upsertAddAccountQuota(dayPO);

                return 1;
            } catch (DuplicateKeyException e) {
                status.setRollbackOnly();
                log.warn("下单幂等拦截 userId:{} outBusinessNo:{}", aggregate.getUserId(), orderEntity.getOutBusinessNo());
                throw new AppException(ResponseCode.INDEX_DUP);
            } catch (Exception e) {
                status.setRollbackOnly();
                log.error("保存下单聚合记录失败 userId:{}", aggregate.getUserId(), e);
                throw e;
            }
        });
    }

    @Override
    public void saveCreatePartakeOrderAggregate(CreatePartakeOrderAggregate aggregate) {
        String userId = aggregate.getUserId();
        Long activityId = aggregate.getActivityId();
        ActivityAccountMonthEntity monthEntity = aggregate.getActivityAccountMonthEntity();
        ActivityAccountDayEntity dayEntity = aggregate.getActivityAccountDayEntity();
        UserRaffleOrderEntity orderEntity = aggregate.getUserRaffleOrderEntity();

        transactionTemplate.execute(status -> {
            try {
                // 1. 总账户乐观锁扣减
                int totalUpdateCount =
                        raffleActivityAccountDao.updateActivityAccountSubtractionQuota(RaffleActivityAccount
                                .builder()
                                .userId(userId)
                                .activityId(activityId)
                                .build());
                if (1 != totalUpdateCount) {
                    status.setRollbackOnly();
                    throw new AppException(ResponseCode.ACCOUNT_QUOTA_ERROR);
                }

                // 2. 月账户 Upsert 原子扣减/初始化
                int monthUpdateCount = raffleActivityAccountMonthDao.upsertAddAccountQuota(RaffleActivityAccountMonth
                        .builder()
                        .userId(userId)
                        .activityId(activityId)
                        .month(monthEntity.getMonth())
                        .monthCount(monthEntity.getMonthCount())
                        .monthCountSurplus(monthEntity.getMonthCountSurplus() - 1)
                        .build());
                if (monthUpdateCount == 0) {
                    status.setRollbackOnly();
                    throw new AppException(ResponseCode.ACCOUNT_MONTH_QUOTA_ERROR);
                }

                // 3. 日账户 Upsert 原子扣减/初始化
                int dayUpdateCount = raffleActivityAccountDayDao.upsertAddAccountQuota(RaffleActivityAccountDay
                        .builder()
                        .userId(userId)
                        .activityId(activityId)
                        .day(dayEntity.getDay())
                        .dayCount(dayEntity.getDayCount())
                        .dayCountSurplus(dayEntity.getDayCountSurplus() - 1)
                        .build());
                if (dayUpdateCount == 0) {
                    status.setRollbackOnly();
                    throw new AppException(ResponseCode.ACCOUNT_DAY_QUOTA_ERROR);
                }

                // 4. 写入抽奖单
                userRaffleOrderDao.insert(UserRaffleOrder
                        .builder()
                        .userId(orderEntity.getUserId())
                        .activityId(orderEntity.getActivityId())
                        .activityName(orderEntity.getActivityName())
                        .strategyId(orderEntity.getStrategyId())
                        .orderId(orderEntity.getOrderId())
                        .orderTime(orderEntity.getOrderTime())
                        .orderState(orderEntity
                                .getOrderState()
                                .getCode())
                        .build());

                return 1;
            } catch (DuplicateKeyException e) {
                status.setRollbackOnly();
                throw new AppException(ResponseCode.INDEX_DUP);
            } catch (Exception e) {
                status.setRollbackOnly();
                throw e;
            }
        });
    }

    @Override
    public boolean subtractionActivitySkuStock(Long sku, String cacheKey, Date endDateTime) {
        long surplus = redisService.decr(cacheKey);
        if (surplus < 0) {
            redisService.setAtomicLong(cacheKey, 0);
            return false;
        }

        if (surplus == 0) {
            eventPublisher.publish(activitySkuStockZeroMessageEvent.exchange(),
                    activitySkuStockZeroMessageEvent.routingKey(),
                    activitySkuStockZeroMessageEvent.buildEventMessage(sku));
        }

        // 占位锁逻辑：确保并发下序号唯一抢占
        String lockKey = cacheKey + Constants.UNDERLINE + surplus;
        long expireMillis = endDateTime.getTime() - System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1);
        Boolean lock = redisService.setNx(lockKey, expireMillis, TimeUnit.MILLISECONDS);
        if (!lock) {
            log.warn("SKU: {} 序号 {} 占位锁获取失败", sku, surplus);
        }
        return lock;
    }

    @Override
    public UserRaffleOrderEntity queryNoUsedRaffleOrder(PartakeRaffleActivityEntity partakeEntity) {
        UserRaffleOrder req = new UserRaffleOrder();
        req.setUserId(partakeEntity.getUserId());
        req.setActivityId(partakeEntity.getActivityId());

        UserRaffleOrder res = userRaffleOrderDao.queryNoUsedRaffleOrder(req);
        if (null == res) return null;

        return UserRaffleOrderEntity
                .builder()
                .userId(res.getUserId())
                .activityId(res.getActivityId())
                .activityName(res.getActivityName())
                .strategyId(res.getStrategyId())
                .orderId(res.getOrderId())
                .orderTime(res.getOrderTime())
                .orderState(UserRaffleOrderStateVO.valueOf(res.getOrderState()))
                .build();
    }

    @Override
    public ActivityAccountEntity queryActivityAccountByUserId(String userId, Long activityId) {
        RaffleActivityAccount req = new RaffleActivityAccount();
        req.setUserId(userId);
        req.setActivityId(activityId);

        RaffleActivityAccount res = raffleActivityAccountDao.queryActivityAccountByUserId(req);
        if (null == res) return null;

        return ActivityAccountEntity
                .builder()
                .userId(res.getUserId())
                .activityId(res.getActivityId())
                .totalCount(res.getTotalCount())
                .totalCountSurplus(res.getTotalCountSurplus())
                .dayCount(res.getDayCount())
                .dayCountSurplus(res.getDayCountSurplus())
                .monthCount(res.getMonthCount())
                .monthCountSurplus(res.getMonthCountSurplus())
                .build();
    }

    @Override
    public ActivityAccountMonthEntity queryActivityAccountMonthByUserId(String userId, Long activityId, String month) {
        RaffleActivityAccountMonth req = new RaffleActivityAccountMonth();
        req.setUserId(userId);
        req.setActivityId(activityId);
        req.setMonth(month);

        RaffleActivityAccountMonth res = raffleActivityAccountMonthDao.queryActivityAccountMonthByUserId(req);
        if (null == res) return null;

        return ActivityAccountMonthEntity
                .builder()
                .userId(res.getUserId())
                .activityId(res.getActivityId())
                .month(res.getMonth())
                .monthCount(res.getMonthCount())
                .monthCountSurplus(res.getMonthCountSurplus())
                .build();
    }

    @Override
    public ActivityAccountDayEntity queryActivityAccountDayByUserId(String userId, Long activityId, String day) {
        RaffleActivityAccountDay req = new RaffleActivityAccountDay();
        req.setUserId(userId);
        req.setActivityId(activityId);
        req.setDay(day);

        RaffleActivityAccountDay res = raffleActivityAccountDayDao.queryActivityAccountDay(req);
        if (null == res) return null;

        return ActivityAccountDayEntity
                .builder()
                .userId(res.getUserId())
                .activityId(res.getActivityId())
                .day(res.getDay())
                .dayCount(res.getDayCount())
                .dayCountSurplus(res.getDayCountSurplus())
                .build();
    }

    @Override
    public List<ActivitySkuEntity> queryActivitySkuListByActivityId(Long activityId) {
        List<RaffleActivitySku> list = raffleActivitySkuDao.queryActivitySkuListByActivityId(activityId);
        return list
                .stream()
                .map(item -> ActivitySkuEntity
                        .builder()
                        .sku(item.getSku())
                        .activityId(item.getActivityId())
                        .activityCountId(item.getActivityCountId())
                        .stockCount(item.getStockCount())
                        .stockCountSurplus(item.getStockCountSurplus())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public Integer queryRaffleActivityAccountDayPartakeCount(Long activityId, String userId) {
        RaffleActivityAccountDay req = RaffleActivityAccountDay
                .builder()
                .activityId(activityId)
                .userId(userId)
                .day(RaffleActivityAccountDay.currentDay())
                .build();
        RaffleActivityAccountDay res = raffleActivityAccountDayDao.queryActivityAccountDay(req);
        if (null == res || res.getDayCount() == null || res.getDayCountSurplus() == null) return 0;
        return res.getDayCount() - res.getDayCountSurplus();
    }

    @Override
    public ActivityAccountEntity queryActivityAccountEntity(Long activityId, String userId) {
        // 1. 查询总账户额度
        RaffleActivityAccount raffleActivityAccount =
                raffleActivityAccountDao.queryActivityAccountByUserId(RaffleActivityAccount
                        .builder()
                        .activityId(activityId)
                        .userId(userId)
                        .build());

        // 如果总账户不存在，直接返回一个初始化的空额度实体
        if (null == raffleActivityAccount) {
            return ActivityAccountEntity
                    .builder()
                    .activityId(activityId)
                    .userId(userId)
                    .totalCount(0)
                    .totalCountSurplus(0)
                    .monthCount(0)
                    .monthCountSurplus(0)
                    .dayCount(0)
                    .dayCountSurplus(0)
                    .build();
        }

        // 2. 查询月、日账户额度
        RaffleActivityAccountMonth raffleActivityAccountMonth =
                raffleActivityAccountMonthDao.queryActivityAccountMonthByUserId(RaffleActivityAccountMonth
                        .builder()
                        .activityId(activityId)
                        .userId(userId)
                        .build());

        RaffleActivityAccountDay raffleActivityAccountDay =
                raffleActivityAccountDayDao.queryActivityAccountDayByUserId(RaffleActivityAccountDay
                        .builder()
                        .activityId(activityId)
                        .userId(userId)
                        .build());

        // 3. 组装并返回领域实体
        return ActivityAccountEntity
                .builder()
                .userId(userId)
                .activityId(activityId)
                .totalCount(raffleActivityAccount.getTotalCount())
                .totalCountSurplus(raffleActivityAccount.getTotalCountSurplus())
                // 组装日额度：若无当日记录，则从总账户配置中初始化额度
                .dayCount(null == raffleActivityAccountDay ? raffleActivityAccount.getDayCount() :
                        raffleActivityAccountDay.getDayCount())
                .dayCountSurplus(null == raffleActivityAccountDay ? raffleActivityAccount.getDayCount() :
                        raffleActivityAccountDay.getDayCountSurplus())
                // 组装月额度：若无当月记录，则从总账户配置中初始化额度
                .monthCount(null == raffleActivityAccountMonth ? raffleActivityAccount.getMonthCount() :
                        raffleActivityAccountMonth.getMonthCount())
                .monthCountSurplus(null == raffleActivityAccountMonth ? raffleActivityAccount.getMonthCount() :
                        raffleActivityAccountMonth.getMonthCountSurplus())
                .build();
    }

    @Override
    public Integer queryRaffleActivityAccountPartakeCount(Long activityId, String userId) {
        // 1. 查询用户活动账户
        RaffleActivityAccount raffleActivityAccount =
                raffleActivityAccountDao.queryActivityAccountByUserId(RaffleActivityAccount
                        .builder()
                        .activityId(activityId)
                        .userId(userId)
                        .build());

        // 2. 账户不存在则返回参与次数为 0 (防止空指针)
        if (null == raffleActivityAccount) return 0;

        // 3. 计算已参与次数：总次数 - 剩余次数
        return raffleActivityAccount.getTotalCount() - raffleActivityAccount.getTotalCountSurplus();
    }

    // --- 简单封装方法保持原有逻辑 ---
    @Override
    public void activitySkuStockConsumeSendQueue(ActivitySkuStockKeyVO vo) {
        String key = Constants.RedisKey.ACTIVITY_SKU_COUNT_QUERY_KEY;
        redisService
                .getDelayedQueue(redisService.getBlockingQueue(key))
                .offer(vo, 3, TimeUnit.SECONDS);
    }

    @Override
    public ActivitySkuStockKeyVO takeQueueValue() {
        return (ActivitySkuStockKeyVO) redisService
                .getBlockingQueue(Constants.RedisKey.ACTIVITY_SKU_COUNT_QUERY_KEY)
                .poll();
    }

    @Override
    public void clearQueueValue() {
        redisService
                .getBlockingQueue(Constants.RedisKey.ACTIVITY_SKU_COUNT_QUERY_KEY)
                .clear();
    }

    @Override
    public void subtractionActivitySkuStock(Long sku) {
        raffleActivitySkuDao.updateActivitySkuStock(sku);
    }

    @Override
    public void updateActivitySkuStockBatch(Long sku, Integer count) {
        raffleActivitySkuDao.updateActivitySkuStockCount(sku, count);
    }

    @Override
    public void zeroOutActivitySkuStock(Long sku) {
        raffleActivitySkuDao.clearActivitySkuStock(sku);
    }

    @Override
    public void setSkuStockZeroFlag(Long sku) {
        redisService.setValue(Constants.RedisKey.ACTIVITY_SKU_STOCK_ZERO_FLAG + sku, "1", 30, TimeUnit.MINUTES);
    }

    @Override
    public boolean isSkuStockZero(Long sku) {
        return redisService.isExists(Constants.RedisKey.ACTIVITY_SKU_STOCK_ZERO_FLAG + sku);
    }

    @Override
    public void cacheActivitySkuStockCount(String key, Integer count) {
        if (redisService.isExists(key)) return;
        redisService.setAtomicLong(key, count);
    }
}