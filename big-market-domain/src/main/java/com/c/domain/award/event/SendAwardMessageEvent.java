package com.c.domain.award.event;

import com.c.types.event.BaseEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.util.Date;

/**
 * 发送奖品领域事件
 * 1. 定义中奖后发放奖品的业务标准，包含消息结构与 MQ 路由契约。
 * 2. 构建包含用户、奖品信息的标准化事件载体，生成用于幂等校验的全局唯一 ID。
 * 3. 衔接抽奖领域与奖品发放基础设施层的标准化信号。
 *
 * @author cyh
 * @date 2026/02/01
 */
@Component
public class SendAwardMessageEvent extends BaseEvent<SendAwardMessageEvent.SendAwardMessage> {

    @Value("${spring.rabbitmq.topic.send_award.exchange}")
    private String exchange;

    @Value("${spring.rabbitmq.topic.send_award.routing-key}")
    private String routingKey;

    /**
     * 构建发送奖品事件消息体
     * 业务逻辑：
     * 1. 自动生成 11 位数字格式的唯一消息 ID，确保奖品发放过程的幂等性。
     * 2. 封装用户 ID 与奖品核心属性，作为事件的有效载荷（Payload）。
     *
     * @param awardMessage 发奖信息实体
     * @return 标准化的事件消息包装对象 {@link EventMessage}
     */
    @Override
    public EventMessage<SendAwardMessage> buildEventMessage(SendAwardMessage awardMessage) {
        return EventMessage.<SendAwardMessage>builder()
                           .id(RandomStringUtils.randomNumeric(11))
                           .timestamp(new Date())
                           .data(awardMessage)
                           .build();
    }

    /**
     * 获取事件关联的交换机名称
     */
    @Override
    public String topic() {
        return exchange;
    }

    /**
     * 获取事件关联的路由键
     */
    public String routingKey() {
        return routingKey;
    }

    /**
     * 发送奖品消息载体对象
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SendAwardMessage {
        /** 用户ID */
        private String userId;
        /** 奖品ID */
        private Integer awardId;
        /** 奖品标题（名称） */
        private String awardTitle;
    }
}