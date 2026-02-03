package com.c.infrastructure.adapter.repository;

import com.c.domain.activity.event.ActivitySkuStockZeroMessageEvent;
import com.c.domain.activity.model.aggregate.CreatePartakeOrderAggregate;
import com.c.domain.activity.model.aggregate.CreateQuotaOrderAggregate;
import com.c.domain.activity.model.entity.*;
import com.c.domain.activity.model.vo.ActivitySkuStockKeyVO;
import com.c.domain.activity.model.vo.ActivityStateVO;
import com.c.domain.activity.model.vo.UserRaffleOrderStateVO;
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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
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
    @Resource
    private IUserRaffleOrderDao userRaffleOrderDao;
    @Resource
    private IRaffleActivityAccountMonthDao raffleActivityAccountMonthDao;
    @Resource
    private IRaffleActivityAccountDayDao raffleActivityAccountDayDao;

    @Override
    public ActivitySkuEntity queryActivitySku(Long sku) {
        // 从数据库查询 SKU 原始信息
        RaffleActivitySku raffleActivitySKU = raffleActivitySkuDao.queryActivitySku(sku);
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
    public void doSaveOrder(CreateQuotaOrderAggregate createQuotaOrderAggregate) {
        try {
            // 提取聚合根中的订单实体
            ActivityOrderEntity activityOrderEntity = createQuotaOrderAggregate.getActivityOrderEntity();

            // 1. 准备订单 PO 数据：记录用户参与流水的详细快照
            RaffleActivityOrder raffleActivityOrder = new RaffleActivityOrder();
            raffleActivityOrder.setUserId(activityOrderEntity.getUserId());
            raffleActivityOrder.setSku(activityOrderEntity.getSku());
            raffleActivityOrder.setActivityId(activityOrderEntity.getActivityId());
            raffleActivityOrder.setActivityName(activityOrderEntity.getActivityName());
            raffleActivityOrder.setStrategyId(activityOrderEntity.getStrategyId());
            raffleActivityOrder.setOrderId(activityOrderEntity.getOrderId());
            raffleActivityOrder.setOrderTime(activityOrderEntity.getOrderTime());
            raffleActivityOrder.setTotalCount(createQuotaOrderAggregate.getTotalCount());
            raffleActivityOrder.setDayCount(createQuotaOrderAggregate.getDayCount());
            raffleActivityOrder.setMonthCount(createQuotaOrderAggregate.getMonthCount());
            raffleActivityOrder.setState(activityOrderEntity.getState().getCode());
            raffleActivityOrder.setOutBusinessNo(activityOrderEntity.getOutBusinessNo());

            // 2. 准备账户 PO 数据：用于更新或初始化用户的参与额度
            RaffleActivityAccount raffleActivityAccount = new RaffleActivityAccount();
            raffleActivityAccount.setUserId(createQuotaOrderAggregate.getUserId());
            raffleActivityAccount.setActivityId(createQuotaOrderAggregate.getActivityId());
            raffleActivityAccount.setTotalCount(createQuotaOrderAggregate.getTotalCount());
            raffleActivityAccount.setTotalCountSurplus(createQuotaOrderAggregate.getTotalCount());
            raffleActivityAccount.setDayCount(createQuotaOrderAggregate.getDayCount());
            raffleActivityAccount.setDayCountSurplus(createQuotaOrderAggregate.getDayCount());
            raffleActivityAccount.setMonthCount(createQuotaOrderAggregate.getMonthCount());
            raffleActivityAccount.setMonthCountSurplus(createQuotaOrderAggregate.getMonthCount());

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
        // 职责：从 Redis 阻塞队列中获取待处理的库存同步任务。
        // 业务背景：高并发下库存先在 Redis 扣减，随后通过该队列异步通知 Job 执行数据库物理更新。
        String cacheKey = Constants.RedisKey.ACTIVITY_SKU_COUNT_QUERY_KEY;
        RBlockingQueue<ActivitySkuStockKeyVO> destinationQueue = redisService.getBlockingQueue(cacheKey);
        return destinationQueue.poll();
    }

    @Override
    public void clearQueueValue() {
        // 职责：强制清空异步库存同步队列。
        // 应用场景：通常在系统维护、库存大规模调整或售罄截断链路时使用，防止旧的过期指令干扰当前库存水位。
        String cacheKey = Constants.RedisKey.ACTIVITY_SKU_COUNT_QUERY_KEY;
        RBlockingQueue<ActivitySkuStockKeyVO> destinationQueue = redisService.getBlockingQueue(cacheKey);
        destinationQueue.clear();
    }

    @Override
    public void subtractionActivitySkuStock(Long sku) {
        // 职责：执行数据库层面的单次库存扣减。
        // 逻辑：调用 DAO 更新记录，底层通常使用 `set stock = stock - 1 where sku = ? and stock > 0`。
        raffleActivitySkuDao.updateActivitySkuStock(sku);
    }

    @Override
    public void updateActivitySkuStockBatch(Long sku, Integer count) {
        // 职责：批量同步 Redis 扣减额到数据库。
        // 核心价值：减少数据库 IO 次数。将 Redis 累积的扣减增量（count）一次性更新到物理库。
        raffleActivitySkuDao.updateActivitySkuStockCount(sku, count);
    }

    @Override
    public void zeroOutActivitySkuStock(Long sku) {
        // 职责：物理库存清零。
        // 场景：当 Redis 探测到库存售罄时，强制将数据库中的剩余库存归零，确保库、表状态最终一致。
        raffleActivitySkuDao.clearActivitySkuStock(sku);
    }

    @Override
    public void setSkuStockZeroFlag(Long sku) {
        // 职责：设置 SKU 售罄标识位。
        // 设计意图：作为“熔断”开关。一旦 Redis 库存扣减至 0，立即设置此标识，拦截后续所有无效的抽奖请求。
        String cacheKey = Constants.RedisKey.ACTIVITY_SKU_STOCK_ZERO_FLAG + sku;
        // 设置 30 分钟有效期，确保覆盖异步 Job 的同步周期，防止缓存过期导致请求穿透到 DB
        redisService.setValue(cacheKey, "1", 30, TimeUnit.MINUTES);
    }

    @Override
    public boolean isSkuStockZero(Long sku) {
        // 职责：检查 SKU 是否已售罄。
        // 作用：抽奖链路最前端的防线，若标识存在则直接拒绝参与，保护核心业务逻辑不被无效流量冲垮。
        String cacheKey = Constants.RedisKey.ACTIVITY_SKU_STOCK_ZERO_FLAG + sku;
        return redisService.isExists(cacheKey);
    }

    @Override
    public void cacheActivitySkuStockCount(String cacheKey, Integer stockCount) {
        // 职责：活动军械库库存预热。
        // 逻辑：采用“不覆盖更新”策略。如果 Redis 已存在值（可能是之前装配好的），则跳过，防止活动进行中误改库存。
        if (redisService.isExists(cacheKey)) return;
        // 使用原子长整型 (AtomicLong) 初始化，支撑后续的 DECR 原子操作
        redisService.setAtomicLong(cacheKey, stockCount);
    }


    @Override
    public UserRaffleOrderEntity queryNoUsedRaffleOrder(PartakeRaffleActivityEntity partakeRaffleActivityEntity) {
        // 1. 组装查询入参：定位特定用户在指定活动下是否存在待使用的单据
        UserRaffleOrder userRaffleOrder = new UserRaffleOrder();
        userRaffleOrder.setUserId(partakeRaffleActivityEntity.getUserId());
        userRaffleOrder.setActivityId(partakeRaffleActivityEntity.getActivityId());

        // 2. 检索数据库：查询状态为“已创建但未消费”的抽奖订单
        // 职责：此操作是参与活动流程幂等性的关键。若用户已扣减额度但未完成抽奖（如由于网络异常中断），
        // 再次进入时应直接返回原订单，避免重复扣除用户活动额度。

        UserRaffleOrder userRaffleOrderRes = userRaffleOrderDao.queryNoUsedRaffleOrder(userRaffleOrder);

        // 3. 业务判空：若无未使用的订单，则返回 null，由上层服务启动新订单创建流程
        if (null == userRaffleOrderRes) return null;

        // 4. 封装领域实体：将基础设施层持久化对象 (PO) 映射为领域层业务实体 (Entity)
        UserRaffleOrderEntity userRaffleOrderEntity = new UserRaffleOrderEntity();
        userRaffleOrderEntity.setUserId(userRaffleOrderRes.getUserId());
        userRaffleOrderEntity.setActivityId(userRaffleOrderRes.getActivityId());
        userRaffleOrderEntity.setActivityName(userRaffleOrderRes.getActivityName());
        userRaffleOrderEntity.setStrategyId(userRaffleOrderRes.getStrategyId());
        userRaffleOrderEntity.setOrderId(userRaffleOrderRes.getOrderId());
        userRaffleOrderEntity.setOrderTime(userRaffleOrderRes.getOrderTime());
        // 状态转换：将数据库中的字符串/数值状态解析为领域层统一的枚举/值对象
        userRaffleOrderEntity.setOrderState(UserRaffleOrderStateVO.valueOf(userRaffleOrderRes.getOrderState()));

        return userRaffleOrderEntity;
    }

    @Override
    public ActivityAccountEntity queryActivityAccountByUserId(String userId, Long activityId) {
        // 1. 组装查询参数：定位用户在特定活动下的总账信息
        RaffleActivityAccount raffleActivityAccount = new RaffleActivityAccount();
        raffleActivityAccount.setUserId(userId);
        raffleActivityAccount.setActivityId(activityId);

        // 2. 执行数据库查询：获取总额度、总余量以及关联的月/日镜像限额配置
        RaffleActivityAccount raffleActivityAccountRes =
                raffleActivityAccountDao.queryActivityAccountByUserId(raffleActivityAccount);

        // 3. 判空处理：若账户不存在，由上层业务决定是抛出异常还是执行初始化逻辑
        if (null == raffleActivityAccountRes) return null;

        // 4. 构建领域实体：将持久化对象转换为活动领域的核心账户模型
        return ActivityAccountEntity.builder().userId(raffleActivityAccountRes.getUserId())
                                    .activityId(raffleActivityAccountRes.getActivityId())
                                    .totalCount(raffleActivityAccountRes.getTotalCount())
                                    .totalCountSurplus(raffleActivityAccountRes.getTotalCountSurplus())
                                    .dayCount(raffleActivityAccountRes.getDayCount())
                                    .dayCountSurplus(raffleActivityAccountRes.getDayCountSurplus())
                                    .monthCount(raffleActivityAccountRes.getMonthCount())
                                    .monthCountSurplus(raffleActivityAccountRes.getMonthCountSurplus())
                                    .build();
    }


    @Override
    public ActivityAccountMonthEntity queryActivityAccountMonthByUserId(String userId, Long activityId,
                                                                        String month) {
        // 1. 组装查询参数：包含月份标识（yyyy-MM），用于检索自然月维度的消费记录
        RaffleActivityAccountMonth raffleActivityAccountMonth = new RaffleActivityAccountMonth();
        raffleActivityAccountMonth.setUserId(userId);
        raffleActivityAccountMonth.setActivityId(activityId);
        raffleActivityAccountMonth.setMonth(month);

        // 2. 检索月度账户：用于校验当前月份的参与频次是否触达上限
        RaffleActivityAccountMonth raffleActivityAccountMonthRes =
                raffleActivityAccountMonthDao.queryActivityAccountMonthByUserId(raffleActivityAccountMonth);

        if (null == raffleActivityAccountMonthRes) return null;

        // 3. 转换对象：封装月度消费快照
        return ActivityAccountMonthEntity.builder().userId(raffleActivityAccountMonthRes.getUserId())
                                         .activityId(raffleActivityAccountMonthRes.getActivityId())
                                         .month(raffleActivityAccountMonthRes.getMonth())
                                         .monthCount(raffleActivityAccountMonthRes.getMonthCount())
                                         .monthCountSurplus(raffleActivityAccountMonthRes.getMonthCountSurplus())
                                         .build();
    }

    @Override
    public ActivityAccountDayEntity queryActivityAccountDayByUserId(String userId, Long activityId,
                                                                    String day) {
        // 1. 组装查询参数：包含日期标识（yyyy-MM-dd），实现最细粒度的日频次管控
        RaffleActivityAccountDay raffleActivityAccountDay = new RaffleActivityAccountDay();
        raffleActivityAccountDay.setUserId(userId);
        raffleActivityAccountDay.setActivityId(activityId);
        raffleActivityAccountDay.setDay(day);

        // 2. 检索日度账户：判断今日剩余可抽奖次数
        RaffleActivityAccountDay raffleActivityAccountDayRes =
                raffleActivityAccountDayDao.queryActivityAccountDayByUserId(raffleActivityAccountDay);

        if (null == raffleActivityAccountDayRes) return null;

        // 3. 转换对象：封装日度消费快照
        return ActivityAccountDayEntity.builder().userId(raffleActivityAccountDayRes.getUserId())
                                       .activityId(raffleActivityAccountDayRes.getActivityId())
                                       .day(raffleActivityAccountDayRes.getDay())
                                       .dayCount(raffleActivityAccountDayRes.getDayCount())
                                       .dayCountSurplus(raffleActivityAccountDayRes.getDayCountSurplus())
                                       .build();
    }

    @Override
    public void saveCreatePartakeOrderAggregate(CreatePartakeOrderAggregate createPartakeOrderAggregate) {
        // 1. 领域模型数据解包 (准备持久化数据上下文)
        String userId = createPartakeOrderAggregate.getUserId();
        Long activityId = createPartakeOrderAggregate.getActivityId();
        ActivityAccountEntity activityAccountEntity = createPartakeOrderAggregate.getActivityAccountEntity();
        ActivityAccountMonthEntity activityAccountMonthEntity =
                createPartakeOrderAggregate.getActivityAccountMonthEntity();
        ActivityAccountDayEntity activityAccountDayEntity =
                createPartakeOrderAggregate.getActivityAccountDayEntity();
        UserRaffleOrderEntity userRaffleOrderEntity = createPartakeOrderAggregate.getUserRaffleOrderEntity();

        // 2. 执行编程式事务：确保多表更新的 ACID 特性，任何一步失败则全量回滚
        transactionTemplate.execute(status -> {
            try {
                // --- 步骤 1：更新总账户额度 ---
                // 采用乐观锁扣减 (update ... set surplus = surplus - 1 where userId = ? and activityId = ? and
                // surplus > 0)
                int totalCount =
                        raffleActivityAccountDao.updateActivityAccountSubtractionQuota(RaffleActivityAccount
                        .builder().userId(userId).activityId(activityId).build());

                if (1 != totalCount) {
                    status.setRollbackOnly(); // 强行回滚事务
                    log.warn("保存参与活动订单失败，总账户额度不足 userId: {} activityId: {}", userId, activityId);
                    throw new AppException(ResponseCode.ACCOUNT_QUOTA_ERROR.getCode(),
                            ResponseCode.ACCOUNT_QUOTA_ERROR.getInfo());
                }


                // --- 步骤 2：处理月维度账户额度 (按需初始化或扣减) ---
                if (createPartakeOrderAggregate.isExistAccountMonth()) {
                    // 已存在当前月份记录，直接执行原子扣减
                    int updateMonthCount =
                            raffleActivityAccountMonthDao.updateActivityAccountMonthSubtractionQuota(RaffleActivityAccountMonth
                            .builder().userId(userId).activityId(activityId)
                            .month(activityAccountMonthEntity.getMonth()).build());
                    if (1 != updateMonthCount) {
                        status.setRollbackOnly();
                        log.warn("月账户额度不足 userId: {} month: {}", userId,
                                activityAccountMonthEntity.getMonth());
                        throw new AppException(ResponseCode.ACCOUNT_MONTH_QUOTA_ERROR.getCode(),
                                ResponseCode.ACCOUNT_MONTH_QUOTA_ERROR.getInfo());
                    }
                } else {
                    // 首次参与本月活动，插入月维度记录
                    raffleActivityAccountMonthDao.insert(RaffleActivityAccountMonth.builder()
                                                                                   .userId(activityAccountMonthEntity.getUserId())
                                                                                   .activityId(activityAccountMonthEntity.getActivityId())
                                                                                   .month(activityAccountMonthEntity.getMonth())
                                                                                   .monthCount(activityAccountMonthEntity.getMonthCount())
                                                                                   .monthCountSurplus(activityAccountMonthEntity.getMonthCountSurplus() - 1) //
                                                                                   // 扣减当前这一次
                                                                                   .build());

                }

                // --- 步骤 3：处理日维度账户额度 (按需初始化或扣减) ---
                if (createPartakeOrderAggregate.isExistAccountDay()) {
                    // 已存在当日记录，执行原子扣减
                    int updateDayCount =
                            raffleActivityAccountDayDao.updateActivityAccountDaySubtractionQuota(RaffleActivityAccountDay
                            .builder().userId(userId).activityId(activityId)
                            .day(activityAccountDayEntity.getDay()).build());
                    if (1 != updateDayCount) {
                        status.setRollbackOnly();
                        log.warn("日账户额度不足 userId: {} day: {}", userId, activityAccountDayEntity.getDay());
                        throw new AppException(ResponseCode.ACCOUNT_DAY_QUOTA_ERROR.getCode(),
                                ResponseCode.ACCOUNT_DAY_QUOTA_ERROR.getInfo());
                    }
                } else {
                    // 首次参与今日活动，插入日维度记录
                    raffleActivityAccountDayDao.insert(RaffleActivityAccountDay.builder()
                                                                               .userId(activityAccountDayEntity.getUserId())
                                                                               .activityId(activityAccountDayEntity.getActivityId())
                                                                               .day(activityAccountDayEntity.getDay())
                                                                               .dayCount(activityAccountDayEntity.getDayCount())
                                                                               .dayCountSurplus(activityAccountDayEntity.getDayCountSurplus() - 1)
                                                                               .build());

                }

                // --- 步骤 4：落库抽奖参与订单 (UserRaffleOrder) ---
                // 这是后续执行抽奖策略的唯一合法依据，必须与额度扣减处于同一事务中
                userRaffleOrderDao.insert(UserRaffleOrder.builder().userId(userRaffleOrderEntity.getUserId())
                                                         .activityId(userRaffleOrderEntity.getActivityId())
                                                         .activityName(userRaffleOrderEntity.getActivityName())
                                                         .strategyId(userRaffleOrderEntity.getStrategyId())
                                                         .orderId(userRaffleOrderEntity.getOrderId())
                                                         .orderTime(userRaffleOrderEntity.getOrderTime())
                                                         .orderState(userRaffleOrderEntity.getOrderState()
                                                                                          .getCode())
                                                         .build());

                return 1; // 事务成功
            } catch (DuplicateKeyException e) {
                // --- 步骤 5：处理幂等性冲突 ---
                // 场景：同一秒内并发请求同一活动，可能导致订单 ID 冲突或月/日表插入重复
                status.setRollbackOnly();
                log.error("保存参与活动订单失败，唯一索引冲突 userId: {} activityId: {}", userId, activityId, e);
                throw new AppException(ResponseCode.INDEX_DUP.getCode(), e);
            }
        });
    }

    @Override
    public List<ActivitySkuEntity> queryActivitySkuListByActivityId(Long activityId) {
        List<RaffleActivitySku> raffleActivitySkus =
                raffleActivitySkuDao.queryActivitySkuListByActivityId(activityId);
        List<ActivitySkuEntity> activitySkuEntities = new ArrayList<>(raffleActivitySkus.size());
        for (RaffleActivitySku raffleActivitySku : raffleActivitySkus) {
            activitySkuEntities.add(ActivitySkuEntity.builder().sku(raffleActivitySku.getSku())
                                                     .activityId(raffleActivitySku.getActivityId())
                                                     .activityCountId(raffleActivitySku.getActivityCountId())
                                                     .stockCount(raffleActivitySku.getStockCount())
                                                     .stockCountSurplus(raffleActivitySku.getStockCountSurplus())
                                                     .build());
        }
        return activitySkuEntities;
    }
}