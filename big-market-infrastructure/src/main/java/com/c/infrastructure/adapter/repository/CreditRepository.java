package com.c.infrastructure.adapter.repository;

import com.c.domain.credit.model.aggregate.TradeAggregate;
import com.c.domain.credit.model.entity.CreditAccountEntity;
import com.c.domain.credit.model.entity.CreditOrderEntity;
import com.c.domain.credit.repository.ICreditRepository;
import com.c.infrastructure.dao.IUserCreditAccountDao;
import com.c.infrastructure.dao.IUserCreditOrderDao;
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
import java.util.concurrent.TimeUnit;

/**
 * 积分仓储实现
 * 职责：结合分布式锁与编程式事务，确保积分交易的一致性与原子性。
 * 说明：分库分表路由由 Sharding 组件自动完成。
 *
 * @author cyh
 * @date 2026/02/08
 */
@Slf4j
@Repository
public class CreditRepository implements ICreditRepository {

    @Resource
    private IRedisService redisService;
    @Resource
    private IUserCreditAccountDao userCreditAccountDao;
    @Resource
    private IUserCreditOrderDao userCreditOrderDao;
    @Resource
    private TransactionTemplate transactionTemplate;

    @Override
    public void saveUserCreditTradeOrder(TradeAggregate tradeAggregate) {
        // 1. 基础参数提取
        String userId = tradeAggregate.getUserId();
        CreditAccountEntity accountEntity = tradeAggregate.getCreditAccountEntity();
        CreditOrderEntity orderEntity = tradeAggregate.getCreditOrderEntity();
        String outBusinessNo = orderEntity.getOutBusinessNo();

        // 2. 构建PO对象
        // 积分账户PO
        UserCreditAccount account = UserCreditAccount
                .builder()
                .userId(userId)
                .totalAmount(accountEntity.getAdjustAmount())
                .availableAmount(accountEntity.getAdjustAmount())
                .build();
        // 积分订单PO
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

        // 3. 分布式锁（核心：仅锁定userId，保护用户积分账户）
        String lockKey = Constants.RedisKey.USER_CREDIT_ACCOUNT_LOCK + userId;
        RLock lock = redisService.getLock(lockKey);
        boolean isLockAcquired = false;

        try {
            // 尝试加锁：等待1秒（避免死等），持有10秒（适配事务最大耗时）
            isLockAcquired = lock.tryLock(1, 10, TimeUnit.SECONDS);
            if (!isLockAcquired) {
                log.error("【积分调账】获取账户锁失败，userId:{} outBusinessNo:{}", userId, outBusinessNo);
                throw new AppException(ResponseCode.CREDIT_ACCOUNT_LOCK_ERROR, "系统繁忙，请稍后重试");
            }

            // 4. 编程式事务：包裹所有数据库操作，保证原子性
            transactionTemplate.execute(status -> {
                try {
                    // ===================== 核心：Upsert/原子更新替代先查后写 =====================
                    if (accountEntity.isForward()) {
                        // 正向加分：Upsert原子操作（不存在则插入，存在则累加）
                        userCreditAccountDao.upsertAddAccountQuota(account);
                    } else if (accountEntity.isReverse()) {
                        // 逆向扣分：原子更新+余额校验（更新行数=0 → 账户不存在/余额不足）
                        int updateRow = userCreditAccountDao.updateSubtractionAmount(account);
                        if (updateRow == 0) {
                            status.setRollbackOnly(); // 标记事务回滚
                            log.warn("【积分调账】扣减失败，余额不足/账户不存在，userId:{}", userId);
                            throw new AppException(ResponseCode.CREDIT_BALANCE_INSUFFICIENT);
                        }
                    }

                    // 5. 保存积分订单（幂等兜底：outBusinessNo唯一索引）
                    userCreditOrderDao.insert(order);
                    return 1; // 事务执行成功

                } catch (DuplicateKeyException e) {
                    // 唯一索引冲突：幂等拦截（重复请求）
                    status.setRollbackOnly();
                    log.warn("【积分调账】订单重复，幂等拦截，userId:{} outBusinessNo:{}", userId, outBusinessNo);
                    throw new AppException(ResponseCode.CREDIT_ORDER_ALREADY_EXISTS);
                } catch (AppException e) {
                    // 业务异常：手动回滚事务
                    status.setRollbackOnly();
                    throw e;
                } catch (Exception e) {
                    // 通用异常：回滚+告警
                    status.setRollbackOnly();
                    log.error("【积分调账】事务执行失败，userId:{}", userId, e);
                    throw new AppException(ResponseCode.UN_ERROR, "积分调账失败");
                }
            });

        } catch (InterruptedException e) {
            // 锁等待被中断：恢复线程中断状态，抛业务异常
            log.error("【积分调账】获取锁线程被中断，userId:{}", userId, e);
            Thread
                    .currentThread()
                    .interrupt(); // 恢复中断状态
            throw new AppException(ResponseCode.UN_ERROR, "操作被中断，请重试");
        } finally {
            // 6. 安全释放锁：仅释放当前线程持有的锁，避免误解锁
            if (isLockAcquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("【积分调账】释放账户锁，userId:{}", userId);
            }
        }
    }
}
