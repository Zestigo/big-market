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
 * 发奖消息领域事件
 * 场景：用户中奖后，通过此事件异步通知发奖系统执行具体的发奖逻辑（积分、实物等）。
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
     * 构建发奖事件消息
     * 1. 生成 11 位随机数字作为消息 ID，用于消费端幂等校验。
     * 2. 封装业务载荷、发送时间及唯一标识。
     *
     * @param awardMessage 业务数据载荷
     * @return 统一格式的消息包装对象
     */
    @Override
    public EventMessage<SendAwardMessage> buildEventMessage(SendAwardMessage awardMessage) {
        return EventMessage
                .<SendAwardMessage>builder()
                .id(RandomStringUtils.randomNumeric(11))
                .timestamp(new Date())
                .data(awardMessage)
                .build();
    }

    @Override
    public String exchange() {
        return exchange;
    }

    @Override
    public String routingKey() {
        return routingKey;
    }

    /**
     * 发奖事件消息体
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SendAwardMessage {
        /** 用户 ID */
        private String userId;
        /** 业务单号 */
        private String orderId;
        /** 奖品 ID */
        private Integer awardId;
        /** 奖品标题 */
        private String awardTitle;
        /** 发奖配置（通常是 JSON 字符串，用于控制发奖逻辑） */
        private String awardConfig;
    }
}