package com.c.domain.award.service;

import com.c.domain.award.event.SendAwardMessageEvent;
import com.c.domain.award.model.aggregate.UserAwardRecordAggregate;
import com.c.domain.award.model.entity.DistributeAwardEntity;
import com.c.domain.award.model.entity.TaskEntity;
import com.c.domain.award.model.entity.UserAwardRecordEntity;
import com.c.domain.award.model.vo.TaskStateVO;
import com.c.domain.award.repository.IAwardRepository;
import com.c.domain.award.service.distribute.IDistributeAward;
import com.c.types.event.BaseEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 中奖记录与奖品分发服务
 * * 职责：
 * 1. 组装中奖记录与 MQ 发送任务。
 * 2. 路由并执行不同类型的奖品发放逻辑。
 *
 * @author cyh
 * @date 2026/02/01
 */
@Slf4j
@Service
public class AwardService implements IAwardService {

    private final IAwardRepository awardRepository;
    private final SendAwardMessageEvent sendAwardMessageEvent;
    private final Map<String, IDistributeAward> distributeAwardMap;

    public AwardService(IAwardRepository awardRepository, SendAwardMessageEvent sendAwardMessageEvent, Map<String,
            IDistributeAward> distributeAwardMap) {
        this.awardRepository = awardRepository;
        this.sendAwardMessageEvent = sendAwardMessageEvent;
        this.distributeAwardMap = distributeAwardMap;
    }

    /**
     * 保存中奖记录及消息任务
     * 1. 组装发奖消息体，生成唯一消息 ID。
     * 2. 构建 Task 实体，绑定 MQ 路由信息。
     * 3. 封装为聚合根交由仓储层进行事务化处理。
     *
     * @param userAwardRecordEntity 用户中奖实体
     */
    @Override
    public void saveUserAwardRecord(UserAwardRecordEntity userAwardRecordEntity) {
        // 1. 构造 MQ 消息载荷
        SendAwardMessageEvent.SendAwardMessage awardMessage = SendAwardMessageEvent.SendAwardMessage
                .builder()
                .userId(userAwardRecordEntity.getUserId())
                .orderId(userAwardRecordEntity.getOrderId())
                .awardId(userAwardRecordEntity.getAwardId())
                .awardTitle(userAwardRecordEntity.getAwardTitle())
                .awardConfig(userAwardRecordEntity.getAwardConfig())
                .build();

        // 2. 构建标准事件消息（包含 ID 和时间戳）
        BaseEvent.EventMessage<SendAwardMessageEvent.SendAwardMessage> awardMessageEventMessage =
                sendAwardMessageEvent.buildEventMessage(awardMessage);

        // 3. 构建本地消息任务记录
        TaskEntity taskEntity = TaskEntity
                .builder()
                .userId(userAwardRecordEntity.getUserId())
                .exchange(sendAwardMessageEvent.exchange())
                .routingKey(sendAwardMessageEvent.routingKey())
                .messageId(awardMessageEventMessage.getId())
                .message(awardMessageEventMessage)
                .state(TaskStateVO.CREATE)
                .build();

        // 4. 构建聚合根并持久化（由仓储层保证事务原子性）
        UserAwardRecordAggregate userAwardRecordAggregate = UserAwardRecordAggregate
                .builder()
                .taskEntity(taskEntity)
                .userAwardRecordEntity(userAwardRecordEntity)
                .build();

        awardRepository.saveUserAwardRecord(userAwardRecordAggregate);
    }

    /**
     * 执行奖品分发逻辑
     * 1. 根据奖品 ID 获取业务标识 Key。
     * 2. 从策略映射表中匹配对应的发奖实现类。
     * 3. 调用对应的发奖服务执行发放逻辑（如积分、实物等）。
     *
     * @param distributeAwardEntity 分发奖品实体
     */
    @Override
    public void distributeAward(DistributeAwardEntity distributeAwardEntity) {
        // 1. 查询奖品业务 Key
        String awardKey = awardRepository.queryAwardKey(distributeAwardEntity.getAwardId());
        if (null == awardKey) {
            log.error("分发奖品失败，奖品 ID 不存在：{}", distributeAwardEntity.getAwardId());
            return;
        }

        // 2. 匹配具体的发奖策略实现
        IDistributeAward distributeAward = distributeAwardMap.get(awardKey);
        if (null == distributeAward) {
            log.error("分发奖品失败，未匹配到对应策略：{}", awardKey);
            throw new RuntimeException("发奖服务不存在：" + awardKey);
        }

        // 3. 执行发奖
        distributeAward.giveOutPrizes(distributeAwardEntity);
    }
}