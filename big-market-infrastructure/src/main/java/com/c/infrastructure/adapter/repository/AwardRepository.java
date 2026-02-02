package com.c.infrastructure.adapter.repository;

import com.alibaba.fastjson.JSON;
import com.c.domain.award.model.aggregate.UserAwardRecordAggregate;
import com.c.domain.award.model.entity.TaskEntity;
import com.c.domain.award.model.entity.UserAwardRecordEntity;
import com.c.domain.award.repositor.IAwardRepository;
import com.c.infrastructure.dao.ITaskDao;
import com.c.infrastructure.dao.IUserAwardRecordDao;
import com.c.infrastructure.event.EventPublisher;
import com.c.infrastructure.po.Task;
import com.c.infrastructure.po.UserAwardRecord;
import com.c.types.enums.ResponseCode;
import com.c.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;

/**
 * 中奖记录仓储实现服务
 * 1. 核心资产落库：负责用户中奖凭证（记录）的持久化。
 * 2. 事务消息协同：采用“本地消息表”模式，在同一个事务内记录业务数据与待发送任务，确保分布式环境下的最终一致性。
 * 3. 异步解耦补偿：事务提交后尝试即时外发 MQ 消息，若失败则由 Job 扫描 Task 表进行补偿重试。
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

    /**
     * 保存用户中奖记录及对应的消息补偿任务
     * * 流程：
     * 1. 开启编程式事务。
     * 2. 插入中奖记录表 (UserAwardRecord) 与 任务补偿表 (Task)。
     * 3. 事务提交后，触发异步 MQ 消息投递。
     * *
     *
     * @param userAwardRecordAggregate 中奖记录聚合根，包含中奖实体及待发布的任务实体
     */
    @Override
    public void saveUserAwardRecord(UserAwardRecordAggregate userAwardRecordAggregate) {

        UserAwardRecordEntity userAwardRecordEntity = userAwardRecordAggregate.getUserAwardRecordEntity();
        TaskEntity taskEntity = userAwardRecordAggregate.getTaskEntity();
        String userId = userAwardRecordEntity.getUserId();
        Long activityId = userAwardRecordEntity.getActivityId();
        Integer awardId = userAwardRecordEntity.getAwardId();

        // 1. PO 对象转换 - 中奖记录
        UserAwardRecord userAwardRecord = UserAwardRecord.builder().userId(userId).activityId(activityId)
                                                         .strategyId(userAwardRecordEntity.getStrategyId())
                                                         .orderId(userAwardRecordEntity.getOrderId())
                                                         .awardId(awardId)
                                                         .awardTitle(userAwardRecordEntity.getAwardTitle())
                                                         .awardTime(userAwardRecordEntity.getAwardTime())
                                                         .awardState(userAwardRecordEntity.getAwardState()
                                                                                          .getCode()).build();
        // 2. PO 对象转换 - 任务记录
        Task task = Task.builder().userId(userId).topic(taskEntity.getTopic())
                        .messageId(taskEntity.getMessageId())
                        .message(JSON.toJSONString(taskEntity.getMessage()))
                        .state(taskEntity.getState().getCode()).build();

        // 3. 执行数据库事务
        transactionTemplate.execute(status -> {
            try {
                // 同库事务内：写入中奖记录
                userAwardRecordDao.insert(userAwardRecord);
                // 同库事务内：写入待发送任务
                taskDao.insert(task);
                return 1;
            } catch (DuplicateKeyException e) {
                status.setRollbackOnly();
                log.error("写入中奖记录，唯一索引冲突 userId: {} activityId: {} awardId: {}", userId, activityId,
                        awardId, e);
                throw new AppException(ResponseCode.INDEX_DUP.getCode(), e);
            }
        });

        // 4. 事务外投递 MQ 消息（尽力投递原则 - 优化：线程池）
        try {
            // 发送消息：若此步成功，则流程结束；若此步失败，系统依赖定时任务扫描 Task 表重新投递
            eventPublisher.publish(task.getTopic(), task.getMessage());
            // 更新任务状态：标记为已完成
            taskDao.updateTaskSendMessageCompleted(task);
        } catch (Exception e) {
            log.error("写入中奖记录，异步发送MQ消息失败（等待Job补偿） userId: {} topic: {}", userId, task.getTopic());
            // 更新任务状态：标记为失败（可选，Job 通常扫描非成功状态的任务）
            taskDao.updateTaskSendMessageFail(task);
        }
    }
}