package com.c.domain.award.service;

import com.c.domain.award.event.SendAwardMessageEvent;
import com.c.domain.award.model.aggregate.UserAwardRecordAggregate;
import com.c.domain.award.model.entity.TaskEntity;
import com.c.domain.award.model.entity.UserAwardRecordEntity;
import com.c.domain.award.model.vo.TaskStateVO;
import com.c.domain.award.repositor.IAwardRepository;
import com.c.types.event.BaseEvent;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * 中奖记录领域服务
 * 1. 逻辑编排：负责将中奖实体（UserAwardRecord）与补偿任务实体（Task）进行关联编排。
 * 2. 消息构建：依托事件领域对象，完成标准的领域事件消息（Message Payload）封装。
 * 3. 聚合封装：构建聚合根，确保数据以整体的形式交付给仓储层进行事务化持久化。
 *
 * @author cyh
 * @date 2026/02/01
 */
@Service
public class AwardService implements IAwardService {

    @Resource
    private IAwardRepository awardRepository;
    @Resource
    private SendAwardMessageEvent sendAwardMessageEvent;

    /**
     * 保存用户中奖记录
     * 1. 组装发奖事件消息，生成全局唯一的 MessageID。
     * 2. 构建任务补偿实体，记录待发送的 Topic 和 消息体内容。
     * 3. 封装聚合根，引导仓储层利用本地事务（Local Transaction）完成双表写入。
     *
     * @param userAwardRecordEntity 用户中奖实体
     */
    @Override
    public void saveUserAwardRecord(UserAwardRecordEntity userAwardRecordEntity) {
        // 1. 构造领域事件消息体 - 定义发奖通知的内容载荷
        SendAwardMessageEvent.SendAwardMessage awardMessage =
                new SendAwardMessageEvent.SendAwardMessage();
        awardMessage.setUserId(userAwardRecordEntity.getUserId());
        awardMessage.setAwardId(userAwardRecordEntity.getAwardId());
        awardMessage.setAwardTitle(userAwardRecordEntity.getAwardTitle());

        // 2. 构建标准的事件包装对象 - 自动生成 EventMessage.id (即 MessageID)
        BaseEvent.EventMessage<SendAwardMessageEvent.SendAwardMessage> awardMessageEventMessage =
                sendAwardMessageEvent.buildEventMessage(awardMessage);

        // 3. 构建任务补偿实体 - 用于实现 Transactional Outbox 模式
        TaskEntity taskEntity = new TaskEntity();
        taskEntity.setUserId(userAwardRecordEntity.getUserId());
        taskEntity.setTopic(sendAwardMessageEvent.topic()); // 获取 MQ 队列 Topic
        taskEntity.setMessageId(awardMessageEventMessage.getId()); // 关键：作为消费幂等键
        taskEntity.setMessage(awardMessageEventMessage); // 完整的消息对象
        taskEntity.setState(TaskStateVO.create); // 初始状态：创建/待发送

        // 4. 构建中奖记录聚合根 - 维护业务记录与任务记录的原子关系
        UserAwardRecordAggregate userAwardRecordAggregate = UserAwardRecordAggregate.builder()
                                                                                    .taskEntity(taskEntity)
                                                                                    .userAwardRecordEntity(userAwardRecordEntity)
                                                                                    .build();

        // 5. 调用仓储层执行原子化保存 - 用户的中奖凭证落库
        awardRepository.saveUserAwardRecord(userAwardRecordAggregate);
    }
}