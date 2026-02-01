package com.c.infrastructure.adapter.repository;

import com.c.domain.activity.event.ActivitySkuStockZeroMessageEvent;
import com.c.domain.activity.model.aggregate.CreateOrderAggregate;
import com.c.domain.activity.model.entity.*;
import com.c.domain.activity.model.vo.ActivitySkuStockKeyVO;
import com.c.domain.activity.model.vo.ActivityStateVO;
import com.c.domain.activity.repositor.IActivityRepository;
import com.c.infrastructure.dao.*;
import com.c.infrastructure.event.EventPublisher;
import com.c.infrastructure.po.*;
import com.c.infrastructure.redis.IRedisService;
import com.c.types.common.Constants;
import com.c.types.enums.ResponseCode;
import com.c.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RDelayedQueue;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * 活动领域仓储实现类
 * 职责：负责活动数据的持久化、缓存管理及异步库存逻辑编排
 *
 * @author cyh
 * @date 2026/01/31
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
    private TransactionTemplate transactionTemplate;
    @Resource
    private ActivitySkuStockZeroMessageEvent activitySkuStockZeroMessageEvent;
    @Resource
    private EventPublisher eventPublisher;

    @Override
    public ActivitySkuEntity queryActivitySku(Long sku) {
        // 从数据库查询 SKU 原始信息
        RaffleActivitySKU raffleActivitySKU = raffleActivitySkuDao.queryActivitySku(sku);
        // 转换为领域实体对象，屏蔽数据库 PO 细节
        return ActivitySkuEntity.builder().sku(raffleActivitySKU.getSku())
                                .activityId(raffleActivitySKU.getActivityId())
                                .activityCountId(raffleActivitySKU.getActivityCountId())
                                .stockCount(raffleActivitySKU.getStockCount())
                                .stockCountSurplus(raffleActivitySKU.getStockCountSurplus()).build();
    }

    @Override
    public ActivityEntity queryRaffleActivityByActivityId(Long activityId) {
        // 拼接活动信息缓存 Key
        String cacheKey = Constants.RedisKey.ACTIVITY_KEY + activityId;
        // 优先从 Redis 获取，命中则直接返回
        ActivityEntity activityEntity = redisService.getValue(cacheKey);
        if (activityEntity != null) return activityEntity;

        // 缓存未命中，穿透到数据库查询
        RaffleActivity raffleActivity = raffleActivityDao.queryRaffleActivityByActivityId(activityId);
        // 构建领域实体
        activityEntity = ActivityEntity.builder().activityId(raffleActivity.getActivityId())
                                       .activityName(raffleActivity.getActivityName())
                                       .activityDesc(raffleActivity.getActivityDesc())
                                       .beginDateTime(raffleActivity.getBeginDateTime())
                                       .endDateTime(raffleActivity.getEndDateTime())
                                       .strategyId(raffleActivity.getStrategyId())
                                       .state(ActivityStateVO.valueOf(raffleActivity.getState())).build();

        // 存入 Redis 供下次使用（此处可根据需求设置过期时间）
        redisService.setValue(cacheKey, activityEntity);
        return activityEntity;
    }

    @Override
    public ActivityCountEntity queryRaffleActivityCountByActivityCountId(Long activityCountId) {
        // 拼接次数配置缓存 Key
        String cacheKey = Constants.RedisKey.ACTIVITY_COUNT_KEY + activityCountId;
        // 缓存查找
        ActivityCountEntity activityCountEntity = redisService.getValue(cacheKey);
        if (activityCountEntity != null) return activityCountEntity;

        // DB 查找配置：包含总、日、月次数限制
        RaffleActivityCount raffleActivityCount =
                raffleActivityCountDao.queryRaffleActivityCountByActivityCountId(activityCountId);
        activityCountEntity = ActivityCountEntity.builder()
                                                 .activityCountId(raffleActivityCount.getActivityCountId())
                                                 .totalCount(raffleActivityCount.getTotalCount())
                                                 .dayCount(raffleActivityCount.getDayCount())
                                                 .monthCount(raffleActivityCount.getMonthCount()).build();

        // 回写缓存
        redisService.setValue(cacheKey, activityCountEntity);
        return activityCountEntity;
    }

    @Override
    public void doSaveOrder(CreateOrderAggregate createOrderAggregate) {
        try {
            // 提取聚合根中的订单实体
            ActivityOrderEntity activityOrderEntity = createOrderAggregate.getActivityOrderEntity();

            // 1. 准备订单 PO 数据：记录用户参与流水的详细快照
            RaffleActivityOrder raffleActivityOrder = new RaffleActivityOrder();
            raffleActivityOrder.setUserId(activityOrderEntity.getUserId());
            raffleActivityOrder.setSku(activityOrderEntity.getSku());
            raffleActivityOrder.setActivityId(activityOrderEntity.getActivityId());
            raffleActivityOrder.setActivityName(activityOrderEntity.getActivityName());
            raffleActivityOrder.setStrategyId(activityOrderEntity.getStrategyId());
            raffleActivityOrder.setOrderId(activityOrderEntity.getOrderId());
            raffleActivityOrder.setOrderTime(activityOrderEntity.getOrderTime());
            raffleActivityOrder.setTotalCount(createOrderAggregate.getTotalCount());
            raffleActivityOrder.setDayCount(createOrderAggregate.getDayCount());
            raffleActivityOrder.setMonthCount(createOrderAggregate.getMonthCount());
            raffleActivityOrder.setState(activityOrderEntity.getState().getCode());
            raffleActivityOrder.setOutBusinessNo(activityOrderEntity.getOutBusinessNo());

            // 2. 准备账户 PO 数据：用于更新或初始化用户的参与额度
            RaffleActivityAccount raffleActivityAccount = new RaffleActivityAccount();
            raffleActivityAccount.setUserId(createOrderAggregate.getUserId());
            raffleActivityAccount.setActivityId(createOrderAggregate.getActivityId());
            raffleActivityAccount.setTotalCount(createOrderAggregate.getTotalCount());
            raffleActivityAccount.setTotalCountSurplus(createOrderAggregate.getTotalCount());
            raffleActivityAccount.setDayCount(createOrderAggregate.getDayCount());
            raffleActivityAccount.setDayCountSurplus(createOrderAggregate.getDayCount());
            raffleActivityAccount.setMonthCount(createOrderAggregate.getMonthCount());
            raffleActivityAccount.setMonthCountSurplus(createOrderAggregate.getMonthCount());

            // 3. 开启编程式事务：保证流水写入与额度变更原子性
            transactionTemplate.execute(status -> {
                try {
                    // 步骤一：写入活动订单（唯一索引 out_business_no 保证幂等）
                    raffleActivityOrderDao.insert(raffleActivityOrder);

                    // 步骤二：尝试更新账户额度（老用户场景）
                    int count = raffleActivityAccountDao.updateAccountQuota(raffleActivityAccount);
                    if (0 == count) {
                        // 步骤三：更新影响行为 0，说明是新用户，执行开户插入
                        try {
                            raffleActivityAccountDao.insert(raffleActivityAccount);
                        } catch (DuplicateKeyException e) {
                            // 极高并发下多个请求同时判定为新用户，其中一个成功后，其余忽略冲突
                            log.warn("并发开户冲突: userId: {}", raffleActivityAccount.getUserId());
                        }
                    }
                    return 1;
                } catch (DuplicateKeyException e) {
                    // 订单索引冲突，说明是重复下单请求，触发回滚并抛出异常
                    status.setRollbackOnly();
                    throw new AppException(ResponseCode.INDEX_DUP.getCode());
                } catch (Exception e) {
                    // 其他未知异常，执行回滚
                    status.setRollbackOnly();
                    log.error("下单聚合事务执行异常", e);
                    throw e;
                }
            });
        } catch (Exception e) {
            log.error("doSaveOrder 异常", e);
            throw e;
        }
    }

    /**
     * 扣减活动SKU库存（Redis原子操作）
     *
     * @param sku         商品SKU
     * @param cacheKey    缓存Key (Constants.RedisKey.ACTIVITY_SKU_STOCK_COUNT_KEY + sku)
     * @param endDateTime 活动结束时间（用于兜底，本逻辑主要利用decr原子性）
     * @return true-扣减成功（允许下单）；false-库存不足
     */
    @Override
    public boolean subtractionActivitySkuStock(Long sku, String cacheKey, Date endDateTime) {
        // 1. 利用 Redis decr 原子性自减，获取扣减后的即时库存编号
        long surplus = redisService.decr(cacheKey);

        // 2. 情况 A：库存不足
        if (surplus < 0) {
            // 修正机制：防止库存变成负数，强制复位为0
            redisService.setAtomicLong(cacheKey, 0);
            return false;
        }

        // 3. 情况 B：触发售罄同步逻辑（正好减到0的那一笔）
        if (surplus == 0) {
            log.info("SKU: {} 抢占最后一份库存成功，触发售罄同步逻辑", sku);
            eventPublisher.publish(activitySkuStockZeroMessageEvent.topic(),
                    activitySkuStockZeroMessageEvent.routingKey(),
                    activitySkuStockZeroMessageEvent.buildEventMessage(sku));
        }

        // 4. 【新增锁逻辑】情况 C：抢占序号占位锁
        // 逻辑：以“cacheKey + 序号”作为唯一的锁名，确保该序号只能被成功扣减一次
        String lockKey = cacheKey + Constants.UNDERLINE + surplus;

        // 计算锁的有效期：活动结束时间 - 当前时间 + 1天兜底
        long expireMillis = endDateTime.getTime() - System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1);

        // 尝试加锁（占坑）
        Boolean lock = redisService.setNx(lockKey, expireMillis, TimeUnit.MILLISECONDS);
        if (!lock) {
            log.warn("SKU: {} 序号 {} 占位锁获取失败，可能存在重复扣减或系统异常", sku, surplus);
        }

        return lock;
    }

    @Override
    public void activitySkuStockConsumeSendQueue(ActivitySkuStockKeyVO activitySkuStockKeyVO) {
        // 定义 Redisson 阻塞队列 Key
        String cacheKey = Constants.RedisKey.ACTIVITY_SKU_COUNT_QUERY_KEY;
        RBlockingQueue<ActivitySkuStockKeyVO> blockingQueue = redisService.getBlockingQueue(cacheKey);
        // 基于阻塞队列封装延迟队列
        RDelayedQueue<ActivitySkuStockKeyVO> delayedQueue = redisService.getDelayedQueue(blockingQueue);
        // 将库存扣减消息放入延迟队列，3 秒后可被消费，实现削峰填谷同步 DB
        delayedQueue.offer(activitySkuStockKeyVO, 3, TimeUnit.SECONDS);
    }

    @Override
    public ActivitySkuStockKeyVO takeQueueValue() {
        // 从阻塞队列中拉取就绪的库存变更指令
        String cacheKey = Constants.RedisKey.ACTIVITY_SKU_COUNT_QUERY_KEY;
        RBlockingQueue<ActivitySkuStockKeyVO> destinationQueue = redisService.getBlockingQueue(cacheKey);
        return destinationQueue.poll();
    }

    @Override
    public void clearQueueValue() {
        // 清空队列：通常用于库存售罄后的链路截断，不再处理过期的同步指令
        String cacheKey = Constants.RedisKey.ACTIVITY_SKU_COUNT_QUERY_KEY;
        RBlockingQueue<ActivitySkuStockKeyVO> destinationQueue = redisService.getBlockingQueue(cacheKey);
        destinationQueue.clear();
    }

    @Override
    public void subtractionActivitySkuStock(Long sku) {
        // 物理扣减：调用 DAO 执行 update stock = stock - 1
        raffleActivitySkuDao.updateActivitySkuStock(sku);
    }

    @Override
    public void updateActivitySkuStockBatch(Long sku, Integer count) {
        // 物理批量扣减：将 Redis 累计扣减量同步至 DB
        raffleActivitySkuDao.updateActivitySkuStockCount(sku, count);
    }

    @Override
    public void setSkuStockZeroFlag(Long sku) {
        String cacheKey = Constants.RedisKey.ACTIVITY_SKU_STOCK_ZERO_FLAG + sku;
        // 设置标识，有效期 30 分钟（足以覆盖 Job 延迟周期）
        redisService.setValue(cacheKey, "1", 30, TimeUnit.MINUTES);
    }

    @Override
    public boolean isSkuStockZero(Long sku) {
        String cacheKey = Constants.RedisKey.ACTIVITY_SKU_STOCK_ZERO_FLAG + sku;
        return redisService.isExists(cacheKey);

    }

    @Override
    public void zeroOutActivitySkuStock(Long sku) {
        // 强制清零：对应售罄事件的最终数据库一致性处理
        raffleActivitySkuDao.clearActivitySkuStock(sku);
    }

    @Override
    public void cacheActivitySkuStockCount(String cacheKey, Integer stockCount) {
        // 库存预热：若 Redis 中不存在该 SKU 库存缓存，则进行初始化
        if (redisService.isExists(cacheKey)) return;
        redisService.setAtomicLong(cacheKey, stockCount);
    }
}