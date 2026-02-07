package com.c.domain.award.event;

import com.c.types.event.BaseEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * 领域事件：中奖奖品发放
 * 业务场景：当用户中奖记录产生后触发，用于异步驱动下游奖品发放系统（如积分增加、实物物流开单等）。
 * 核心职责：规范发奖信号的消息结构，确保抽奖领域与奖品基础设施层的高效解耦。
 * 契约定义：定义了发奖业务专属的交换机 (Exchange) 与路由键 (Routing Key)。
 *
 * @author cyh
 * @date 2026/02/01
 */
@Component
public class SendAwardMessageEvent extends BaseEvent<SendAwardMessageEvent.SendAwardMessage> {

    /** 消息队列交换机名称：对应配置中定义的奖品业务域交换机 */
    @Value("${spring.rabbitmq.topic.send_award.exchange}")
    private String exchange;

    /** 消息队列路由键：对应配置中定义的奖品发放路由规则 */
    @Value("${spring.rabbitmq.topic.send_award.routing-key}")
    private String routingKey;

    /**
     * 构建发送奖品事件的标准消息载体
     * 1. 幂等设计：生成 11 位全局唯一流水号，防止因网络抖动导致的重复发奖。
     * 2. 数据封装：承载用户、奖品 ID 及名称等关键信息，作为消费端处理的原始依据。
     * 3. 链路审计：记录 timestamp 支撑全链路数据追踪与性能统计。
     *
     * @param awardMessage 发奖业务数据主体
     * @return 包含幂等 ID 与业务载荷的标准事件消息对象
     */
    @Override
    public EventMessage<SendAwardMessage> buildEventMessage(SendAwardMessage awardMessage) {
        return EventMessage.<SendAwardMessage>builder()
                           .id(RandomStringUtils.randomNumeric(11)) // 生成流水 ID
                           .timestamp(new Date())                   // 记录时刻
                           .data(awardMessage)                      // 注入载荷
                           .build();
    }

    /**
     * 获取事件关联的交换机名称 (Exchange)
     * 对应 RabbitMQ 中的物理分拨中心。
     */
    @Override
    public String exchange() {
        return exchange;
    }

    /**
     * 获取事件关联的路由键 (Routing Key)
     * 对应 RabbitMQ 中的精准分拣逻辑。
     */
    @Override
    public String routingKey() {
        return routingKey;
    }

    /**
     * 内部类：发奖事件业务负载对象
     * 描述具体的奖品发放要素，确保跨系统传输的数据完整性。
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SendAwardMessage {
        /** 用户唯一 ID */
        private String userId;

        /** 奖品唯一 ID */
        private Integer awardId;

        /** 奖品描述名称（方便日志记录与展示） */
        private String awardTitle;
    }
}