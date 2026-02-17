package com.c.infrastructure.adapter.repository;

import com.alibaba.fastjson.JSON;
import com.c.domain.credit.model.aggregate.TradeAggregate;
import com.c.domain.credit.model.entity.CreditAccountEntity;
import com.c.domain.credit.model.entity.CreditOrderEntity;
import com.c.domain.credit.model.entity.TaskEntity;
import com.c.domain.credit.repository.ICreditRepository;
import com.c.infrastructure.dao.ITaskDao;
import com.c.infrastructure.dao.IUserCreditAccountDao;
import com.c.infrastructure.dao.IUserCreditOrderDao;
import com.c.infrastructure.event.EventPublisher;
import com.c.infrastructure.po.Task;
import com.c.infrastructure.po.UserCreditAccount;
import com.c.infrastructure.po.UserCreditOrder;
import com.c.infrastructure.redis.IRedisService;
import com.c.types.common.Constants;
import com.c.types.enums.ResponseCode;
import com.c.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

/**
 * 积分仓储实现
 *
 * @author cyh
 * @date 2026/02/09
 */
@Slf4j
@Repository
public class CreditRepository implements ICreditRepository {

    @Resource
    private ITaskDao taskDao;
    @Resource
    private IRedisService redisService;
    @Resource
    private IUserCreditAccountDao userCreditAccountDao;
    @Resource
    private IUserCreditOrderDao userCreditOrderDao;
    @Resource
    private TransactionTemplate transactionTemplate;
    @Resource
    private EventPublisher eventPublisher;

    @Override
    public void saveUserCreditTradeOrder(TradeAggregate tradeAggregate) {
        String userId = tradeAggregate.getUserId();
        CreditAccountEntity accountEntity = tradeAggregate.getCreditAccountEntity();
        CreditOrderEntity orderEntity = tradeAggregate.getCreditOrderEntity();
        TaskEntity taskEntity = tradeAggregate.getTaskEntity();
        String outBusinessNo = orderEntity.getOutBusinessNo();

        // 1. 构建账户 PO (UserCreditAccount)
        UserCreditAccount account = UserCreditAccount
                .builder()
                .userId(userId)
                .totalAmount(accountEntity.getAdjustAmount())
                .availableAmount(accountEntity.getAdjustAmount())
                .build();

        // 2. 构建订单 PO (UserCreditOrder)
        UserCreditOrder order = UserCreditOrder
                .builder()
                .userId(userId)
                .orderId(orderEntity.getOrderId())
                .tradeName(orderEntity
                        .getTradeName()
                        .getCode())
                .tradeType(orderEntity
                        .getTradeType()
                        .getCode())
                .tradeAmount(orderEntity.getTradeAmount())
                .outBusinessNo(outBusinessNo)
                .build();

        // 3. 构建任务 PO (Task)
        Task task = Task
                .builder()
                .userId(taskEntity.getUserId())
                .exchange(taskEntity.getExchange())
                .routingKey(taskEntity.getRoutingKey())
                .messageId(taskEntity.getMessageId())
                .message(JSON.toJSONString(taskEntity.getMessage()))
                .state(taskEntity
                        .getState()
                        .getCode())
                .build();

        // 4. 获取分布式锁：仅锁定 userId 保护账户资源
        /* 锁定 userId 可确保同一用户在并发调账时，账户余额的更新是串行的，防止产生并发竞争导致的余额错误。
           此处不加 outBusinessNo 是因为锁的目标是“资源（账户）”，而非“操作（订单）”。 */
        String lockKey = Constants.RedisKey.USER_CREDIT_ACCOUNT_LOCK + userId;
        RLock lock = redisService.getLock(lockKey);
        boolean isLockAcquired = false;

        try {
            isLockAcquired = lock.tryLock(1, 10, TimeUnit.SECONDS);
            if (!isLockAcquired) {
                log.error("【积分调账】获取账户锁失败 userId:{} outBusinessNo:{}", userId, outBusinessNo);
                throw new AppException(ResponseCode.CREDIT_ACCOUNT_LOCK_ERROR);
            }

            // 5. 编程式事务执行持久化
            transactionTemplate.execute(status -> {
                try {
                    // 更新余额：正向增加或逆向扣减
                    if (accountEntity.isForward()) {
                        userCreditAccountDao.upsertAddAccountQuota(account);
                    } else {
                        int updateRow = userCreditAccountDao.updateSubtractionAmount(account);
                        if (updateRow == 0) {
                            status.setRollbackOnly();
                            throw new AppException(ResponseCode.CREDIT_BALANCE_INSUFFICIENT);
                        }
                    }

                    // 保存订单与任务（利用 order 的唯一索引 outBusinessNo 实现幂等）
                    userCreditOrderDao.insert(order);
                    taskDao.insert(task);
                    return 1;
                } catch (DuplicateKeyException e) {
                    status.setRollbackOnly();
                    log.warn("【积分调账】幂等拦截，订单已存在 userId:{} outBusinessNo:{}", userId, outBusinessNo);
                    throw new AppException(ResponseCode.CREDIT_ORDER_ALREADY_EXISTS);
                } catch (Exception e) {
                    status.setRollbackOnly();
                    log.error("【积分调账】事务异常 userId:{}", userId, e);
                    throw e;
                }
            });

        } catch (InterruptedException e) {
            Thread
                    .currentThread()
                    .interrupt();
            throw new AppException(ResponseCode.UN_ERROR);
        } finally {
            if (isLockAcquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }

        // 6. 事务外异步发布事件（失败由定时任务补偿）
        try {
            // 增加打印，对比这里的 key 和你 Config 里的 key 是否一致
            log.info("【积分调账】准备发布消息 Exchange: {}, RoutingKey: {}, Message: {}", task.getExchange(),
                    task.getRoutingKey(), task.getMessage());

            eventPublisher.publish(task.getExchange(), task.getRoutingKey(), task.getMessage());
            taskDao.updateTaskSendMessageCompleted(task);

            log.info("【积分调账】消息发布成功且 Task 状态已更新 userId:{}", userId);
        } catch (Exception e) {
            log.error("【积分调账】消息发布失败 userId:{}", userId, e); // 打印堆栈
            taskDao.updateTaskSendMessageFail(task);
        }
    }

    @Override
    public CreditAccountEntity queryUserCreditAccount(String userId) {
        // 1. 构造查询参数对象
        UserCreditAccount accountReq = UserCreditAccount
                .builder()
                .userId(userId)
                .build();

        // 2. 执行数据库查询
        UserCreditAccount userCreditAccount = userCreditAccountDao.queryUserCreditAccount(accountReq);

        // 3. 结果空值处理：若账户不存在，则返回零值余额实体
        if (null == userCreditAccount) {
            return CreditAccountEntity
                    .builder()
                    .userId(userId)
                    .adjustAmount(BigDecimal.ZERO)
                    .build();
        }

        // 4. 封装领域实体并返回
        return CreditAccountEntity
                .builder()
                .userId(userId)
                .adjustAmount(userCreditAccount.getAvailableAmount())
                .build();
    }
}