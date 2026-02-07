package com.c.domain.award.service;

import com.c.domain.award.event.SendAwardMessageEvent;
import com.c.domain.award.model.aggregate.UserAwardRecordAggregate;
import com.c.domain.award.model.entity.TaskEntity;
import com.c.domain.award.model.entity.UserAwardRecordEntity;
import com.c.domain.award.model.vo.TaskStateVO;
import com.c.domain.award.repository.IAwardRepository;
import com.c.types.event.BaseEvent;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * 领域服务：中奖记录编排
 * <p>
 * 职责：
 * 1. 业务编排：将用户中奖实体与本地消息任务实体进行逻辑关联。
 * 2. 契约构建：基于领域事件定义，提取交换机(Exchange)与路由键(Routing Key)等通信要素。
 * 3. 聚合生命周期管理：构建聚合根对象，确保中奖记录与任务表在仓储层实现事务一致性。
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
     * * 过程：
     * 1. 组装发奖业务载荷（Payload），包含用户、奖品等核心中奖信息。
     * 2. 创建领域事件消息，利用事件模板自动生成全局唯一标识（Message ID）。
     * 3. 封装本地消息任务（Task），记录精确的路由配置信息。
     * 4. 交付聚合根至仓储层，通过数据库本地事务确保“记录+任务”的双写原子性。
     *
     * @param userAwardRecordEntity 用户中奖领域实体
     */
    @Override
    public void saveUserAwardRecord(UserAwardRecordEntity userAwardRecordEntity) {
        // 1. 构造发奖业务消息载荷
        SendAwardMessageEvent.SendAwardMessage awardMessage = SendAwardMessageEvent.SendAwardMessage
                .builder()
                .userId(userAwardRecordEntity.getUserId())
                .awardId(userAwardRecordEntity.getAwardId())
                .awardTitle(userAwardRecordEntity.getAwardTitle())
                .build();

        // 2. 构建标准事件包装对象 - 内部包含生成的时间戳与幂等 ID
        BaseEvent.EventMessage<SendAwardMessageEvent.SendAwardMessage> awardMessageEventMessage =
                sendAwardMessageEvent.buildEventMessage(awardMessage);

        // 3. 构建本地消息任务实体 - 明确 Exchange 与 Routing Key 路由配置
        TaskEntity taskEntity = TaskEntity
                .builder()
                .userId(userAwardRecordEntity.getUserId())
                .exchange(sendAwardMessageEvent.exchange())
                .routingKey(sendAwardMessageEvent.routingKey())
                .messageId(awardMessageEventMessage.getId())
                .message(awardMessageEventMessage)
                .state(TaskStateVO.CREATE)
                .build();

        // 4. 构建中奖记录聚合根 - 封装业务记录与任务记录的依赖关系
        UserAwardRecordAggregate userAwardRecordAggregate = UserAwardRecordAggregate
                .builder()
                .taskEntity(taskEntity)
                .userAwardRecordEntity(userAwardRecordEntity)
                .build();

        // 5. 调用仓储层执行持久化 - 引导进入 DAO 层的事务控制范围
        awardRepository.saveUserAwardRecord(userAwardRecordAggregate);
    }
}