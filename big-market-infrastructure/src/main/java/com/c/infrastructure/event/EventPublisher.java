package com.c.infrastructure.event;

import com.alibaba.fastjson.JSON;
import com.c.types.event.BaseEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author cyh
 * @description 消息事件发布服务
 * 职责：负责将领域内产生的事件消息，通过 RabbitMQ 基础设施投递到指定的交换机/主题中。
 * @date 2026/01/28
 */
@Slf4j
@Component
public class EventPublisher {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    /**
     * 发布 MQ 消息
     *
     * @param topic        消息队列主题（Exchange 名称）
     * @param eventMessage 标准事件消息体（包含 ID、时间戳、泛型数据内容）
     */
    public void publish(String topic, BaseEvent.EventMessage<?> eventMessage) {
        try {
            // 1. 将事件对象序列化为 JSON 字符串
            // 统一使用 JSON 传输可以保证跨语言、跨服务的兼容性，也方便在 RabbitMQ 后台查看明文
            String messageJson = JSON.toJSONString(eventMessage);

            // 2. 发送消息
            // 采用 convertAndSend 方式，Spring 会自动处理底层的 AMQP 通信
            rabbitTemplate.convertAndSend(topic, messageJson);

            log.info("发送MQ消息成功 topic:{} message:{}", topic, messageJson);
        } catch (Exception e) {
            // 3. 异常处理与追踪
            // 如果 MQ 服务宕机或网络异常，记录完整的堆栈信息并向上抛出，以便触发上层业务（如：事务回滚或重试机制）
            log.error("发送MQ消息失败 topic:{} message:{}", topic, JSON.toJSONString(eventMessage), e);
            throw e;
        }
    }
}