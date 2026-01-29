package com.c.infrastructure.adapter.repository;

import com.c.domain.activity.event.ActivitySkuStockZeroMessageEvent;
import com.c.domain.activity.model.aggregate.CreateOrderAggregate;
import com.c.domain.activity.model.entity.ActivityCountEntity;
import com.c.domain.activity.model.entity.ActivityEntity;
import com.c.domain.activity.model.entity.ActivityOrderEntity;
import com.c.domain.activity.model.entity.ActivitySkuEntity;
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
 * @author cyh
 * @description 抽奖活动仓储实现服务
 * 职责：连接领域层与基础设施层，处理活动配置查询、下单聚合持久化及库存一致性维护。
 * 架构：接入 Sharding-JDBC 分库分表，通过 userId 路由；配合 Redis 与 Redisson 实现高性能读写。
 * @date 2026/01/28
 */
@Slf4j
@Repository
public class ActivityRepository implements IActivityRepository {

    @Resource
    private IRedisService redisService; // Redis 操作封装，处理缓存读写
    @Resource
    private IRaffleActivityDao raffleActivityDao; // 活动主表访问
    @Resource
    private IRaffleActivitySkuDao raffleActivitySkuDao; // 活动 SKU 表访问
    @Resource
    private IRaffleActivityCountDao raffleActivityCountDao; // 活动次数配置表访问
    @Resource
    private IRaffleActivityOrderDao raffleActivityOrderDao; // 抽奖单表访问
    @Resource
    private IRaffleActivityAccountDao raffleActivityAccountDao; // 用户活动账户额度表访问
    @Resource
    private TransactionTemplate transactionTemplate; // 编程式事务模版，手动控制事务提交回滚
    @Resource
    private ActivitySkuStockZeroMessageEvent activitySkuStockZeroMessageEvent;
    @Resource
    private EventPublisher eventPublisher;

    /**
     * 根据 SKU 编号查询活动 SKU 实体信息
     */
    @Override
    public ActivitySkuEntity queryActivitySku(Long sku) {
        // 1. 调用 DAO 从数据库查询 SKU 持久化对象 (PO)
        RaffleActivitySKU raffleActivitySKU = raffleActivitySkuDao.queryActivitySku(sku);
        // 2. 将 PO 对象映射/转换为 Domain 层的实体对象 (Entity)，实现层间解耦
        return ActivitySkuEntity.builder().sku(raffleActivitySKU.getSku()) // 库存单位编码
                                .activityId(raffleActivitySKU.getActivityId()) // 关联的活动 ID
                                .activityCountId(raffleActivitySKU.getActivityCountId()) // 关联的次数限制配置 ID
                                .stockCount(raffleActivitySKU.getStockCount()) // 总物理库存
                                .stockCountSurplus(raffleActivitySKU.getStockCountSurplus()) // 剩余物理库存
                                .build();
    }

    /**
     * 查询抽奖活动配置（优先走 Redis 缓存）
     */
    @Override
    public ActivityEntity queryRaffleActivityByActivityId(Long activityId) {
        // 1. 拼装缓存 Key，前缀定义在 Constants 类中
        String cacheKey = Constants.RedisKey.ACTIVITY_KEY + activityId;
        // 2. 尝试从缓存获取数据
        ActivityEntity activityEntity = redisService.getValue(cacheKey);
        // 3. 缓存命中则直接返回，避免数据库 IO
        if (activityEntity != null) return activityEntity;

        // 4. 缓存失效，查询数据库主表记录
        RaffleActivity raffleActivity = raffleActivityDao.queryRaffleActivityByActivityId(activityId);
        // 5. 将数据库 PO 转换为领域模型 Entity
        activityEntity = ActivityEntity.builder().activityId(raffleActivity.getActivityId()) // 活动 ID
                                       .activityName(raffleActivity.getActivityName()) // 活动名称
                                       .activityDesc(raffleActivity.getActivityDesc()) // 活动描述
                                       .beginDateTime(raffleActivity.getBeginDateTime()) // 活动开始时间
                                       .endDateTime(raffleActivity.getEndDateTime()) // 活动结束时间
                                       .strategyId(raffleActivity.getStrategyId()) // 关联抽奖策略 ID
                                       .state(ActivityStateVO.valueOf(raffleActivity.getState())) // 活动状态：枚举转换
                                       .build();

        // 6. 将查询结果同步至 Redis 缓存，方便下次访问（旁路缓存写策略）
        redisService.setValue(cacheKey, activityEntity);
        return activityEntity;
    }

    /**
     * 查询活动参与次数限制配置（带缓存逻辑）
     */
    @Override
    public ActivityCountEntity queryRaffleActivityCountByActivityCountId(Long activityCountId) {
        // 1. 尝试从 Redis 缓存获取次数配置
        String cacheKey = Constants.RedisKey.ACTIVITY_COUNT_KEY + activityCountId;
        ActivityCountEntity activityCountEntity = redisService.getValue(cacheKey);
        if (activityCountEntity != null) return activityCountEntity;

        // 2. 缓存未命中，查库并封装实体
        RaffleActivityCount raffleActivityCount =
                raffleActivityCountDao.queryRaffleActivityCountByActivityCountId(activityCountId);
        activityCountEntity = ActivityCountEntity.builder()
                                                 .activityCountId(raffleActivityCount.getActivityCountId()) // 次数配置 ID
                                                 .totalCount(raffleActivityCount.getTotalCount()) // 总可参与次数
                                                 .dayCount(raffleActivityCount.getDayCount()) // 每日可参与次数
                                                 .monthCount(raffleActivityCount.getMonthCount()) // 每月可参与次数
                                                 .build();

        // 3. 回写缓存并返回
        redisService.setValue(cacheKey, activityCountEntity);
        return activityCountEntity;
    }

    /**
     * 【核心】保存下单聚合根：包含生成订单记录与扣减用户活动账户额度
     */
    @Override
    public void doSaveOrder(CreateOrderAggregate createOrderAggregate) {
        try {
            // 1. 获取领域聚合根中的订单实体数据
            ActivityOrderEntity activityOrderEntity = createOrderAggregate.getActivityOrderEntity();

            // 2. 手动装配抽奖订单持久化对象 (PO)
                    RaffleActivityOrder raffleActivityOrder = new RaffleActivityOrder();
            raffleActivityOrder.setUserId(activityOrderEntity.getUserId()); // 设置用户 ID（此字段为 Sharding-JDBC
            // 的分片键）
            raffleActivityOrder.setSku(activityOrderEntity.getSku()); // SKU 编号
            raffleActivityOrder.setActivityId(activityOrderEntity.getActivityId()); // 活动 ID
            raffleActivityOrder.setActivityName(activityOrderEntity.getActivityName()); // 活动名
            raffleActivityOrder.setStrategyId(activityOrderEntity.getStrategyId()); // 策略 ID
            raffleActivityOrder.setOrderId(activityOrderEntity.getOrderId()); // 系统生成的唯一订单号
            raffleActivityOrder.setOrderTime(activityOrderEntity.getOrderTime()); // 下单时间
            raffleActivityOrder.setTotalCount(createOrderAggregate.getTotalCount()); // 参与总次数
            raffleActivityOrder.setDayCount(createOrderAggregate.getDayCount()); // 日次数
            raffleActivityOrder.setMonthCount(createOrderAggregate.getMonthCount()); // 月次数
            raffleActivityOrder.setState(activityOrderEntity.getState().getCode()); // 订单状态 (待使用/已使用等)
            raffleActivityOrder.setOutBusinessNo(activityOrderEntity.getOutBusinessNo()); // 外部业务流水号（幂等关键）

            // 3. 装配账户额度更新对象 (PO)
            RaffleActivityAccount raffleActivityAccount = new RaffleActivityAccount();
            raffleActivityAccount.setUserId(createOrderAggregate.getUserId()); // 用户 ID（分片键）
            raffleActivityAccount.setActivityId(createOrderAggregate.getActivityId()); // 活动 ID
            raffleActivityAccount.setTotalCount(createOrderAggregate.getTotalCount()); // 需要初始化/更新的总次数
            raffleActivityAccount.setTotalCountSurplus(createOrderAggregate.getTotalCount()); // 剩余总次数
            raffleActivityAccount.setDayCount(createOrderAggregate.getDayCount()); // 日次数
            raffleActivityAccount.setDayCountSurplus(createOrderAggregate.getDayCount()); // 剩余日次数
            raffleActivityAccount.setMonthCount(createOrderAggregate.getMonthCount()); // 月次数
            raffleActivityAccount.setMonthCountSurplus(createOrderAggregate.getMonthCount()); // 剩余月次数

            // 4. 执行编程式事务流程
            transactionTemplate.execute(status -> {
                try {
                    // [动作 A]：插入抽奖订单记录
                    // 数据库 raffle_activity_order 表对 out_business_no 设有唯一索引。
                    // 若同一个外部业务单号重复请求，此处会直接抛出 DuplicateKeyException 异常。
                    raffleActivityOrderDao.insert(raffleActivityOrder);

                    // [动作 B]：尝试更新账户额度记录
                    // updateAccountQuota 执行的是 SQL: update ... set count = count - 1 where user_id = ? and
                    // activity_id = ?
                    // 这种方式利用了 MySQL 的行级锁和原子性，返回值 count 代表本次 SQL 真正修改的行数。
                    int count = raffleActivityAccountDao.updateAccountQuota(raffleActivityAccount);

                    // [动作 C]：处理“首次参加”开户场景
                    // 如果 count == 0，说明该用户在此活动下还没有账户记录，需要执行初始化插入。
                    if (0 == count) {
                        try {
                            // 为该用户在 DB 中创建专属的活动账户记录
                            raffleActivityAccountDao.insert(raffleActivityAccount);
                        } catch (DuplicateKeyException e) {
                            // 【高并发防御】：若两个线程同时判断 count=0 并执行 insert，
                            // 数据库的唯一索引 (user_id + activity_id) 会拦截第二个请求。
                            // 此处捕获冲突报错，确保主流程订单保存成功（只要有账户就行，谁创建的不重要）。
                            log.warn("并发开户场景下触发索引冲突，忽略即可: userId: {}", raffleActivityAccount.getUserId());
                        }
                    }

                    // 5. 整个 Lambda 块执行完成无异常，返回 1 信号，事务框架会自动执行 Commit
                    return 1;
                } catch (DuplicateKeyException e) {
                    // 捕获订单唯一键冲突：代表订单已持久化，不允许重复创建
                    status.setRollbackOnly(); // 标记底层数据库事务回滚
                    log.error("检测到重复下单请求，触发幂等保护：userId: {} outBusinessNo: {}",
                            raffleActivityOrder.getUserId(), raffleActivityOrder.getOutBusinessNo());
                    // 抛出自定义业务异常，通知上层业务（如提示用户：请勿重复操作）
                    throw new AppException(ResponseCode.INDEX_DUP.getCode());
                } catch (Exception e) {
                    // 捕获其他任何异常（网络闪断、数据库宕机等）
                    status.setRollbackOnly(); // 回滚所有已写入的数据
                    log.error("下单聚合事务执行异常：", e);
                    throw e; // 继续上抛由外部全局异常处理器处理
                }
            });
        } catch (Exception e) {
            // 捕获事务外层的逻辑执行错误
            log.error("doSaveOrder 外部执行捕获异常：", e);
            throw e;
        }
    }

    /**
     * 将预扣成功的库存记录压入 Redisson 延迟队列
     */
    @Override
    public void activitySkuStockConsumeSendQueue(ActivitySkuStockKeyVO activitySkuStockKeyVO) {
        // 1. 获取阻塞队列（作为延迟队列数据流转的目标终点）
        String cacheKey = Constants.RedisKey.ACTIVITY_SKU_COUNT_QUERY_KEY;
        RBlockingQueue<ActivitySkuStockKeyVO> blockingQueue = redisService.getBlockingQueue(cacheKey);
        // 2. 获取延迟队列（充当数据的“缓冲池”）
        RDelayedQueue<ActivitySkuStockKeyVO> delayedQueue = redisService.getDelayedQueue(blockingQueue);
        // 3. 将数据放入延迟队列，并设定 3 秒后过期。
        // 💡 意义：给数据库事务留出 3 秒提交缓冲期，防止异步更新库存时查不到还没入库的订单。
        delayedQueue.offer(activitySkuStockKeyVO, 3, TimeUnit.SECONDS);
    }

    /**
     * 从库存同步阻塞队列中弹出一个待处理的任务对象
     * 在高并发秒杀/抽奖场景下，Redis 预扣库存非常快，但数据库同步慢。
     * 该方法负责从“任务缓冲区”获取那些 Redis 已经扣减成功、等待回写到 MySQL 的库存记录。
     *
     * @return ActivitySkuStockKeyVO 包含：SKU编号、活动ID、甚至是扣减序号。若队列暂无数据，则返回 null。
     */
    @Override
    public ActivitySkuStockKeyVO takeQueueValue() {
        // 1. 获取 Redis 中定义的队列 Key
        String cacheKey = Constants.RedisKey.ACTIVITY_SKU_COUNT_QUERY_KEY;

        // 2. 通过 redisService 获取 Redisson 封装的分布式阻塞队列（RBlockingQueue）
        //    RBlockingQueue 在 Redis 内部对应一个普通的 LIST 结构。
        //    Redisson 对其进行了封装，使其具备了类似 Java 标准库中 BlockingQueue 的语义（如阻塞等待、超时弹出等）。
        RBlockingQueue<ActivitySkuStockKeyVO> destinationQueue = redisService.getBlockingQueue(cacheKey);

        // 3. 执行 poll() 操作，从队列头部（左侧）弹出一个元素
        //    A. 指令下达：向 Redis 发送一条 LPOP 指令（或者带有阻塞性质的 BLPOP，取决于你是否设置等待时间）。
        //    B. 原子弹出：Redis 保证该操作是原子的。即使有 100 个消费线程同时调这个方法，一个元素也只会被一个线程拿到。
        //    C. 序列化转换：Redisson 会自动将 Redis 里的二进制/JSON 数据反序列化回 ActivitySkuStockKeyVO 对象。
        //    D. 状态反馈：
        //       - 如果队列里有数据：返回该对象，并在 Redis 队列中永久删除该元素。
        //       - 如果队列为空：poll() 方法会立即返回 null（非阻塞返回）。
        return destinationQueue.poll();
    }

    /**
     * 手动清空队列（管理功能）
     */
    @Override
    public void clearQueueValue() {
        String cacheKey = Constants.RedisKey.ACTIVITY_SKU_COUNT_QUERY_KEY;
        RBlockingQueue<ActivitySkuStockKeyVO> destinationQueue = redisService.getBlockingQueue(cacheKey);
        destinationQueue.clear(); // 调用 Redisson 接口清空 Redis List
    }

    /**
     * 物理更新数据库中的 SKU 库存（减 1）
     */
    @Override
    public void updateActivitySkuStock(Long sku) {
        // 调用 DAO 执行 SQL: update raffle_activity_sku set stock_surplus = stock_surplus - 1 where sku = ?
        raffleActivitySkuDao.updateActivitySkuStock(sku);
    }

    /**
     * 强制物理同步清空数据库 SKU 库存（归 0）
     */
    @Override
    public void clearActivitySkuStock(Long sku) {
        // 场景：当 Redis 缓存库存已经彻底售罄，调用此方法将 DB 数据强制刷为 0
        raffleActivitySkuDao.clearActivitySkuStock(sku);
    }

    /**
     * 缓存活动 SKU 库存数量（库存预热/初始化）
     * 利用 Redis 的原子长整型（AtomicLong）将 DB 物理库存映射为分布式原子计数器。
     */
    @Override
    public void cacheActivitySkuStockCount(String cacheKey, Integer stockCount) {
        // 1. 幂等性校验：检查 Redis 中是否已存在该 Key。
        // 如果 Key 已存在（代表已装配过），则直接跳过，防止活动进行中误调装配接口导致正在变化的缓存库存被覆盖。
        if (redisService.isExists(cacheKey)) return;

        // 2. 原子初始化：使用 Redis 的 set 指令将数值存入。
        // 这里使用 setAtomicLong 是为了后续能直接使用 decr 指令进行原子扣减。
        redisService.setAtomicLong(cacheKey, stockCount);
    }

    /**
     * 原子扣减活动 SKU 库存（高性能预减 + 运营防误操作锁）
     * 1. 采用 Redis 原生 DECR 原子递减，确保高并发下的扣减绝对原子性。
     * 2. 引入“库存镜像锁”机制：将扣减后的序号作为唯一 Key 加锁，确保每一份发出去的库存都具有不可伪造的“身份证”。
     *
     * @param sku         库存单元标识，用于构建售罄消息。
     * @param cacheKey    库存计数器的 Redis Key。
     * @param endDateTime 活动结束时间，用于计算锁的 TTL。
     * @return boolean    扣减及加锁是否成功。
     */
    @Override
    public boolean subtractionActivitySkuStock(Long sku, String cacheKey, Date endDateTime) {
        // [步骤 A] 原子递减：Redis 执行 DECR 指令，并立即返回扣减后的值。
        // 该操作在 Redis 单线程模型下是线程安全的，不存在多个请求同时拿到同一个 surplus 的情况。
        long surplus = redisService.decr(cacheKey);

        // [步骤 B] 边界分支处理：库存耗尽场景
        if (surplus == 0) {
            // 场景：恰好扣完最后一个库存。
            // 逻辑：触发“售罄”领域事件，通过 MQ 通知持久化层（DB）将物理库存对齐为 0。
            // 意义：实现缓存与数据库的最终一致性，触发前端或下游逻辑的售罄展示。
            eventPublisher.publish(activitySkuStockZeroMessageEvent.topic(),
                    activitySkuStockZeroMessageEvent.buildEventMessage(sku));
            return false; // 虽然扣到了 0，但在本业务逻辑中，最后一个库存通常交由 lock 逻辑处理，这里返回 false 代表当前瞬时触发了熔断
        } else if (surplus < 0) {
            // 场景：库存已经为 0 后的超额请求。
            // 逻辑：由于 DECR 会将值减为负数（-1, -2...），为了维护计数器的正确性，强制将其回置/恢复为 0。
            redisService.setAtomicLong(cacheKey, 0);
            return false; // 明确反馈：库存不足，无法获取抽奖权限
        }

        // [步骤 C] 分段式库存锁逻辑
        // 1. 锁键构造：使用 cacheKey + 下划线 + 扣减后的序号（如 activity_sku_100_99）。
        //    意义：这种设计将“虚拟数值”变成了“实体锁”。每一个扣减序号都是唯一的，即使发生数据恢复，序号对应的锁也不会重复。
        String lockKey = cacheKey + Constants.UNDERLINE + surplus;

        // 2. 锁有效期计算：活动剩余时长 + 1 天延迟补偿。
        //    意义：确保在活动进行期间以及结束后的结算期内，该笔库存占用记录在 Redis 中保持“已锁定”状态。
        long expireMillis = endDateTime.getTime() - System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1);

        // 3. 执行分布式锁加锁 (SETNX)：利用 setNx 操作的互斥性。
        //    【运营容错逻辑】：
        //    即便运营在后台人工干预手动增加了库存，导致 Redis Key 被重置或回增，
        //    由于之前的序号锁 (lockKey) 依然存在且未过期，新的请求若生成了重复的序号，setNx 将返回 false。
        //    这就从技术底层封死了因为人工操作失误（如库存回滚）导致的超卖风险。
        Boolean lock = redisService.setNx(lockKey, expireMillis, TimeUnit.MILLISECONDS);

        // 4. 日志记录：若加锁失败（说明该库存序号已被占用），属于异常竞争或数据异常，需留痕审计。
        if (!lock) {
            log.info("活动sku库存加锁失败，检测到序号冲突或重复扣减: {}", lockKey);
        }

        // 5. 反馈结果：只有 DECR 成功且 SETNX 加锁成功的请求，才被视为合法获取了抽奖资格。
        return lock;
    }
}