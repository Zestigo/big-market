package com.c.infrastructure.adapter.repository;

import com.alibaba.fastjson.JSON;
import com.c.domain.award.model.aggregate.GiveOutPrizesAggregate;
import com.c.domain.award.model.aggregate.UserAwardRecordAggregate;
import com.c.domain.award.model.entity.TaskEntity;
import com.c.domain.award.model.entity.UserAwardRecordEntity;
import com.c.domain.award.model.entity.UserCreditAwardEntity;
import com.c.domain.award.model.vo.AccountStatusVO;
import com.c.domain.award.repository.IAwardRepository;
import com.c.infrastructure.dao.*;
import com.c.infrastructure.event.EventPublisher;
import com.c.infrastructure.po.Task;
import com.c.infrastructure.po.UserAwardRecord;
import com.c.infrastructure.po.UserCreditAccount;
import com.c.infrastructure.po.UserRaffleOrder;
import com.c.types.enums.ResponseCode;
import com.c.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;

/**
 * 仓储服务实现：中奖记录管理与发奖任务调度
 * <p>
 * 职责：
 * 1. 负责中奖流水、发奖任务、用户积分账户等核心数据的持久化。
 * 2. 采用 [本地消息表] 模式，利用数据库本地事务保证业务逻辑与消息通知的最终一致性。
 *
 * @author cyh
 * @since 2026/02/01
 */
@Slf4j
@Component
public class AwardRepository implements IAwardRepository {

    @Resource
    private IAwardDao awardDao;
    @Resource
    private ITaskDao taskDao;
    @Resource
    private IUserAwardRecordDao userAwardRecordDao;
    @Resource
    private IUserRaffleOrderDao userRaffleOrderDao;
    @Resource
    private IUserCreditAccountDao userCreditAccountDao;
    @Resource
    private TransactionTemplate transactionTemplate;
    @Resource
    private EventPublisher eventPublisher;

    /**
     * 保存用户中奖记录及本地消息任务
     * 处理逻辑：
     * 1. 映射领域实体至 PO 模型。
     * 2. 在本地事务中执行：插入中奖记录、插入任务表、占用抽奖订单。
     * 3. 事务提交后，即时尝试发送 MQ 消息。
     *
     * @param userAwardRecordAggregate 中奖记录聚合根，包含中奖实体及待发任务实体
     */
    @Override
    public void saveUserAwardRecord(UserAwardRecordAggregate userAwardRecordAggregate) {

        UserAwardRecordEntity userAwardRecordEntity = userAwardRecordAggregate.getUserAwardRecordEntity();
        TaskEntity taskEntity = userAwardRecordAggregate.getTaskEntity();

        String userId = userAwardRecordEntity.getUserId();
        String orderId = userAwardRecordEntity.getOrderId();
        Long activityId = userAwardRecordEntity.getActivityId();

        // 1. 数据映射 (Entity -> PO)
        UserAwardRecord userAwardRecord = UserAwardRecord
                .builder()
                .userId(userId)
                .activityId(activityId)
                .strategyId(userAwardRecordEntity.getStrategyId())
                .orderId(orderId)
                .awardId(userAwardRecordEntity.getAwardId())
                .awardTitle(userAwardRecordEntity.getAwardTitle())
                .awardTime(userAwardRecordEntity.getAwardTime())
                .awardState(userAwardRecordEntity
                        .getAwardState()
                        .getCode())
                .build();

        Task task = Task
                .builder()
                .userId(userId)
                .exchange(taskEntity.getExchange())
                .routingKey(taskEntity.getRoutingKey())
                .messageId(taskEntity.getMessageId())
                .message(JSON.toJSONString(taskEntity.getMessage()))
                .state(taskEntity
                        .getState()
                        .getCode())
                .build();

        UserRaffleOrder userRaffleOrder = UserRaffleOrder
                .builder()
                .userId(userId)
                .orderId(orderId)
                .build();

        // 2. 执行本地事务
        transactionTemplate.execute(status -> {
            try {
                // [步骤1] 写入中奖记录流水
                userAwardRecordDao.insert(userAwardRecord);

                // [步骤2] 写入本地消息表（为后续 MQ 发送失败提供补偿依据）
                taskDao.insert(task);

                // [步骤3] 更新抽奖单状态为“已使用”，利用行锁或唯一索引实现幂等防止重复中奖
                int count = userRaffleOrderDao.updateUserRaffleOrderStateUsed(userRaffleOrder);
                if (count != 1) {
                    status.setRollbackOnly();
                    log.error("保存中奖记录失败，抽奖单已被占用或不存在 userId: {} orderId: {}", userId, orderId);
                    throw new AppException(ResponseCode.ACTIVITY_ORDER_ERROR);
                }
                return 1;
            } catch (DuplicateKeyException e) {
                status.setRollbackOnly();
                log.error("保存中奖记录失败，唯一索引冲突（重复提交拦截） userId: {} orderId: {}", userId, orderId, e);
                throw new AppException(ResponseCode.INDEX_DUP, e);
            } catch (Exception e) {
                status.setRollbackOnly();
                log.error("保存中奖记录失败，系统异常 userId: {} orderId: {}", userId, orderId, e);
                throw e;
            }
        });

        // 3. 事务外执行 MQ 实时推送（尽力而为，若失败由 Job 扫描 Task 表重发）
        try {
            eventPublisher.publish(task.getExchange(), task.getRoutingKey(), task.getMessage());
            taskDao.updateTaskSendMessageCompleted(task);
        } catch (Exception e) {
            log.error("中奖记录异步投递失败（待 Job 补偿） userId: {} messageId: {}", userId, task.getMessageId(), e);
            taskDao.updateTaskSendMessageFail(task);
        }
    }

    /**
     * 根据奖品 ID 查询该奖品的发奖策略配置
     *
     * @param awardId 奖品 ID
     * @return 配置字符串（通常为 JSON），用于决定发奖的具体动作（如积分额度、实物邮寄属性等）
     */
    @Override
    public String queryAwardConfig(Integer awardId) {
        return awardDao.queryAwardConfigByAwardId(awardId);
    }

    /**
     * 执行奖品发放落地逻辑（主要处理积分、余额等账户变动类奖品）
     * <p>
     * 特性：
     * 1. 自动开户：若用户积分账户不存在则自动初始化。
     * 2. 状态机控流：通过中奖记录的状态更新（待发 -> 已发）保证发奖动作的幂等性。
     *
     * @param giveOutPrizesAggregate 发奖聚合根，包含用户 ID、积分信息及记录状态
     */
    @Override
    public void saveGiveOutPrizesAggregate(GiveOutPrizesAggregate giveOutPrizesAggregate) {
        String userId = giveOutPrizesAggregate.getUserId();
        UserCreditAwardEntity userCreditAwardEntity = giveOutPrizesAggregate.getUserCreditAwardEntity();
        UserAwardRecordEntity userAwardRecordEntity = giveOutPrizesAggregate.getUserAwardRecordEntity();

        UserAwardRecord userAwardRecord = UserAwardRecord
                .builder()
                .userId(userId)
                .orderId(userAwardRecordEntity.getOrderId())
                .awardState(userAwardRecordEntity
                        .getAwardState()
                        .getCode())
                .build();

        UserCreditAccount userCreditAccount = UserCreditAccount
                .builder()
                .userId(userId)
                .totalAmount(userCreditAwardEntity.getCreditAmount())
                .availableAmount(userCreditAwardEntity.getCreditAmount())
                .accountStatus(AccountStatusVO.OPEN.getCode())
                .build();

        transactionTemplate.execute(status -> {
            try {
                // [步骤1] 状态/流水先行：这是幂等的关键哨兵
                // 将记录更新为完成态（SQL 需带上: WHERE order_id = ? AND state = 'wait'）
                int updateAwardCount = userAwardRecordDao.updateAwardRecordCompletedState(userAwardRecord);

                if (0 == updateAwardCount) {
                    // 如果更新失败，说明：1.记录不存在 2.状态已经改过了（幂等拦截）
                    log.warn("发奖记录幂等拦截：记录已处理或不存在 userId:{} orderId:{}", userId, userAwardRecord.getOrderId());
                    // 注意：这里不需要回滚，因为这可能是一次正常的重复请求，直接退出即可
                    return 0;
                }

                // [步骤2] 资产操作：只有状态改成功了，才真正去加钱
                // 由于有步骤 1 的状态锁死，这里绝对不会重复累加
                userCreditAccountDao.upsertAddAccountQuota(userCreditAccount);

                return 1;
            } catch (DuplicateKeyException e) {
                status.setRollbackOnly();
                log.error("发奖唯一索引冲突 userId:{} orderId:{}", userId, userAwardRecord.getOrderId(), e);
                throw new AppException(ResponseCode.INDEX_DUP, e);
            } catch (Exception e) {
                status.setRollbackOnly();
                log.error("发奖事务执行失败 userId:{} orderId:{}", userId, userAwardRecord.getOrderId(), e);
                throw new AppException(ResponseCode.UN_ERROR, e);
            }
        });
    }

    /**
     * 根据奖品 ID 查询其唯一的业务标识 Key
     *
     * @param awardId 奖品 ID
     * @return 奖品业务 Key（如："coupon_v1", "credit_score"），用于逻辑判断或调用三方接口
     */
    @Override
    public String queryAwardKey(Integer awardId) {
        return awardDao.queryAwardKeyByAwardId(awardId);
    }
}