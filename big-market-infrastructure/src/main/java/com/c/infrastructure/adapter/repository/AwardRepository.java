package com.c.infrastructure.adapter.repository;

import com.alibaba.fastjson.JSON;
import com.c.domain.award.model.aggregate.UserAwardRecordAggregate;
import com.c.domain.award.model.entity.TaskEntity;
import com.c.domain.award.model.entity.UserAwardRecordEntity;
import com.c.domain.award.repository.IAwardRepository;
import com.c.infrastructure.dao.ITaskDao;
import com.c.infrastructure.dao.IUserAwardRecordDao;
import com.c.infrastructure.dao.IUserRaffleOrderDao;
import com.c.infrastructure.event.EventPublisher;
import com.c.infrastructure.po.Task;
import com.c.infrastructure.po.UserAwardRecord;
import com.c.infrastructure.po.UserRaffleOrder;
import com.c.types.enums.ResponseCode;
import com.c.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;

/**
 * 仓储服务实现：中奖记录与任务落地
 * <p>
 * 职责：
 * 1. 本地事务控制：确保中奖凭证记录、任务消息记录、抽奖单状态更新在同一个数据库事务内完成。
 * 2. 最终一致性保障：利用本地消息表模式，解决业务操作与 MQ 投递的原子性问题。
 * 3. 实时发送与补偿机制：事务提交后尝试即时外发，若失败则标记状态由 Job 进行后续补偿。
 *
 * @author cyh
 * @date 2026/02/01
 */
@Slf4j
@Component
public class AwardRepository implements IAwardRepository {

    @Resource
    private ITaskDao taskDao;
    @Resource
    private IUserAwardRecordDao userAwardRecordDao;
    @Resource
    private TransactionTemplate transactionTemplate;
    @Resource
    private EventPublisher eventPublisher;
    @Resource
    private IUserRaffleOrderDao userRaffleOrderDao;

    /**
     * 执行保存用户中奖记录及异步任务消息
     * <p>
     * 过程：
     * 1. 构建 PO 对象：将领域层聚合根拆解为数据库对应的持久化对象。
     * 2. 执行事务操作：原子化写入中奖记录表、任务补偿表，并更新抽奖单状态为已使用。
     * 3. 事务后置发送：事务成功提交后，尝试立即将消息投递至 MQ，并更新任务状态。
     *
     * @param userAwardRecordAggregate 中奖记录聚合根
     */
    @Override
    public void saveUserAwardRecord(UserAwardRecordAggregate userAwardRecordAggregate) {

        UserAwardRecordEntity userAwardRecordEntity = userAwardRecordAggregate.getUserAwardRecordEntity();
        TaskEntity taskEntity = userAwardRecordAggregate.getTaskEntity();

        String userId = userAwardRecordEntity.getUserId();
        String orderId = userAwardRecordEntity.getOrderId();
        Long activityId = userAwardRecordEntity.getActivityId();
        Integer awardId = userAwardRecordEntity.getAwardId();

        // 1. PO 对象转换 - 中奖记录实体映射
        UserAwardRecord userAwardRecord = UserAwardRecord
                .builder()
                .userId(userId)
                .activityId(activityId)
                .strategyId(userAwardRecordEntity.getStrategyId())
                .orderId(orderId)
                .awardId(awardId)
                .awardTitle(userAwardRecordEntity.getAwardTitle())
                .awardTime(userAwardRecordEntity.getAwardTime())
                .awardState(userAwardRecordEntity
                        .getAwardState()
                        .getCode())
                .build();

        // 2. PO 对象转换 - 任务补偿实体映射
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

        // 3. PO 对象转换 - 用户抽奖单更新实体
        UserRaffleOrder userRaffleOrder = UserRaffleOrder
                .builder()
                .userId(userId)
                .orderId(orderId)
                .build();

        // 4. 执行数据库本地事务
        transactionTemplate.execute(status -> {
            try {
                // 写入中奖凭证
                userAwardRecordDao.insert(userAwardRecord);

                // 写入本地消息任务
                taskDao.insert(task);

                // 更新中奖单状态为已使用（防重抽的关键动作）
                int count = userRaffleOrderDao.updateUserRaffleOrderStateUsed(userRaffleOrder);
                if (count != 1) {
                    status.setRollbackOnly();
                    log.error("保存中奖记录失败，抽奖单状态更新异常 userId: {} orderId: {}", userId, orderId);
                    throw new AppException(ResponseCode.ACTIVITY_ORDER_ERROR);
                }
                return 1;
            } catch (DuplicateKeyException e) {
                status.setRollbackOnly();
                log.error("保存中奖记录失败，唯一索引冲突 userId: {} orderId: {}", userId, orderId, e);
                throw new AppException(ResponseCode.INDEX_DUP, e);
            } catch (Exception e) {
                status.setRollbackOnly();
                log.error("保存中奖记录失败，系统异常 userId: {} orderId: {}", userId, orderId, e);
                throw e;
            }
        });

        // 5. 事务外异步投递 MQ 消息
        try {
            // 使用标准的 Exchange 和 RoutingKey 进行发布
            eventPublisher.publish(task.getExchange(), task.getRoutingKey(), task.getMessage());

            // 发送成功后更新任务状态为已完成
            taskDao.updateTaskSendMessageCompleted(task);
        } catch (Exception e) {
            // 失败时记录日志并标记为失败，依赖后续 Job 扫描 Task 表执行重发补偿
            log.error("中奖记录异步投递失败（待 Job 补偿） userId: {} messageId: {} exchange: {}", userId, task.getMessageId(),
                    task.getExchange(), e);
            taskDao.updateTaskSendMessageFail(task);
        }
    }
}