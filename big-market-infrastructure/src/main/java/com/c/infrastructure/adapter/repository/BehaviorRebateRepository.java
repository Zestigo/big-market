package com.c.infrastructure.adapter.repository;

import com.alibaba.fastjson.JSON;
import com.c.domain.rebate.model.aggregate.BehaviorRebateAggregate;
import com.c.domain.rebate.model.entity.BehaviorRebateOrderEntity;
import com.c.domain.rebate.model.entity.TaskEntity;
import com.c.domain.rebate.model.vo.BehaviorTypeVO;
import com.c.domain.rebate.model.vo.DailyBehaviorRebateVO;
import com.c.domain.rebate.repository.IBehaviorRebateRepository;
import com.c.infrastructure.dao.IDailyBehaviorRebateDao;
import com.c.infrastructure.dao.ITaskDao;
import com.c.infrastructure.dao.IUserBehaviorRebateOrderDao;
import com.c.infrastructure.event.EventPublisher;
import com.c.infrastructure.po.DailyBehaviorRebate;
import com.c.infrastructure.po.Task;
import com.c.infrastructure.po.UserBehaviorRebateOrder;
import com.c.types.enums.ResponseCode;
import com.c.types.exception.AppException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 仓储实现：行为返利
 * 职责：处理返利配置检索、流水记录持久化、以及基于本地消息表的可靠消息投递逻辑。
 *
 * @author cyh
 * @date 2026/02/05
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class BehaviorRebateRepository implements IBehaviorRebateRepository {

    private final IDailyBehaviorRebateDao dailyBehaviorRebateDao;
    private final IUserBehaviorRebateOrderDao userBehaviorRebateOrderDao;
    private final ITaskDao taskDao;
    private final TransactionTemplate transactionTemplate;
    private final EventPublisher eventPublisher;

    @Override
    public List<DailyBehaviorRebateVO> queryDailyBehaviorRebateConfig(BehaviorTypeVO behaviorType) {
        List<DailyBehaviorRebate> dailyBehaviorRebates =
                dailyBehaviorRebateDao.queryDailyBehaviorRebateByBehaviorType(behaviorType.getCode());
        return dailyBehaviorRebates
                .stream()
                .map(item -> DailyBehaviorRebateVO
                        .builder()
                        .behaviorType(item.getBehaviorType())
                        .rebateDesc(item.getRebateDesc())
                        .rebateType(item.getRebateType())
                        .rebateConfig(item.getRebateConfig())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public void saveUserRebateRecord(String userId, List<BehaviorRebateAggregate> behaviorRebateAggregates) {
        // 1. 事务内执行：流水单据 + 补偿任务
        transactionTemplate.execute(status -> {
            try {
                for (BehaviorRebateAggregate aggregate : behaviorRebateAggregates) {
                    BehaviorRebateOrderEntity orderEntity = aggregate.getBehaviorRebateOrderEntity();
                    TaskEntity taskEntity = aggregate.getTaskEntity();

                    // 1.1 保存返利流水
                    userBehaviorRebateOrderDao.insert(UserBehaviorRebateOrder
                            .builder()
                            .userId(orderEntity.getUserId())
                            .orderId(orderEntity.getOrderId())
                            .behaviorType(orderEntity.getBehaviorType())
                            .rebateDesc(orderEntity.getRebateDesc())
                            .rebateType(orderEntity.getRebateType())
                            .rebateConfig(orderEntity.getRebateConfig())
                            .outBusinessNo(orderEntity.getOutBusinessNo())
                            .bizId(orderEntity.getBizId())
                            .build());

                    // 1.2 保存消息任务（本地消息表）
                    taskDao.insert(Task
                            .builder()
                            .userId(taskEntity.getUserId())
                            .exchange(taskEntity.getExchange())
                            .routingKey(taskEntity.getRoutingKey())
                            .messageId(taskEntity.getMessageId())
                            .message(JSON.toJSONString(taskEntity.getMessage()))
                            .state(taskEntity
                                    .getState()
                                    .getCode())
                            .build());
                }
                return 1;
            } catch (DuplicateKeyException e) {
                status.setRollbackOnly();
                log.error("写入返利记录失败，唯一索引冲突 userId: {}", userId);
                throw new AppException(ResponseCode.INDEX_DUP, e);
            } catch (Exception e) {
                status.setRollbackOnly();
                log.error("写入返利记录失败，系统异常 userId: {}", userId, e);
                throw e;
            }
        });

        // 2. 事务外执行：实时尝试投递消息
        for (BehaviorRebateAggregate aggregate : behaviorRebateAggregates) {
            TaskEntity taskEntity = aggregate.getTaskEntity();
            Task taskUpdate = Task
                    .builder()
                    .userId(taskEntity.getUserId())
                    .messageId(taskEntity.getMessageId())
                    .build();

            try {
                // 2.1 实时发布
                eventPublisher.publish(taskEntity.getExchange(), taskEntity.getRoutingKey(), taskEntity.getMessage());
                // 2.2 更新为成功状态
                taskDao.updateTaskSendMessageCompleted(taskUpdate);
            } catch (Exception e) {
                // 2.3 异常记录，由 Job 扫表补偿
                log.error("实时发送失败，切换补偿模式 | userId: {} | msgId: {}", userId, taskEntity.getMessageId());
                taskDao.updateTaskSendMessageFail(taskUpdate);
            }
        }
    }

    @Override
    public List<BehaviorRebateOrderEntity> queryOrderByOutBusinessNo(String userId, String outBusinessNo) {
        // 1. 构建查询请求对象
        UserBehaviorRebateOrder userBehaviorRebateOrder = UserBehaviorRebateOrder
                .builder()
                .userId(userId)
                .outBusinessNo(outBusinessNo)
                .build();

        // 2. 查询数据库流水记录
        List<UserBehaviorRebateOrder> userBehaviorRebateOrderResList =
                userBehaviorRebateOrderDao.queryOrderByOutBusinessNo(userBehaviorRebateOrder);

        // 3. 集合对象转换 (使用 Stream API 优化)
        return userBehaviorRebateOrderResList
                .stream()
                .map(item -> BehaviorRebateOrderEntity
                        .builder()
                        .userId(item.getUserId())
                        .orderId(item.getOrderId())
                        .behaviorType(item.getBehaviorType())
                        .rebateDesc(item.getRebateDesc())
                        .rebateType(item.getRebateType())
                        .rebateConfig(item.getRebateConfig())
                        .outBusinessNo(item.getOutBusinessNo())
                        .bizId(item.getBizId())
                        .build())
                .collect(Collectors.toList());
    }

}