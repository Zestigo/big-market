package com.c.infrastructure.event;

import com.c.types.event.BaseEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 消息事件发布服务
 * 1. 基础设施层实现：负责将领域层产生的领域事件（Domain Event）投递至消息中间件。
 * 2. 消息可靠性：利用 CorrelationData 支撑 RabbitMQ 的 Publisher Confirm 机制。
 * 3. 序列化解耦：通过配置 Jackson2JsonMessageConverter 实现对象到 JSON 的自动转换。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventPublisher {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 【推荐】发布标准领域事件
     * 适用场景：领域层直接触发的消息推送，需包含完整事件上下文（ID、时间戳、Payload）。
     *
     * @param exchange     交换机名称，定义事件所属的消息域
     * @param routingKey   路由键，决定消息在交换机内的流向
     * @param eventMessage 统一事件包装对象，内部封装了业务实体数据
     */
    public void publish(String exchange, String routingKey, BaseEvent.EventMessage<?> eventMessage) {
        if (eventMessage == null) {
            log.warn("MQ投递预警：尝试发布空消息载体，已自动拦截。Exchange: {}, RoutingKey: {}", exchange, routingKey);
            return;
        }

        try {
            // 绑定唯一消息ID，用于生产端确认(ConfirmCallback)及链路追踪
            CorrelationData correlationData = new CorrelationData(eventMessage.getId());

            // 执行投递：对象会被 Jackson 转换器自动序列化为 JSON 字符串
            rabbitTemplate.convertAndSend(exchange, routingKey, eventMessage, correlationData);

            log.info("MQ消息指令下发成功 | ID: {} | Exchange: {} | RoutingKey: {}", eventMessage.getId(), exchange,
                    routingKey);
        } catch (Exception e) {
            log.error("MQ消息指令下发异常 | ID: {} | 错误信息: {}", eventMessage.getId(), e.getMessage(), e);
            // 抛出运行时异常，确保在上层事务逻辑中能够触发必要的处理逻辑
            throw new RuntimeException("RabbitMQ message dispatch failed", e);
        }
    }

    /**
     * 【兼容】发布原始 JSON 格式任务消息
     * 适用场景：本地消息表（Task表）补偿机制。
     * 由于任务表中的 message 字段已持久化为 JSON 字符串，此方法避免二次序列化导致的转义问题。
     *
     * @param topic       交换机名称（此处 topic 对应 RabbitMQ 的 Exchange）
     * @param messageJson 已序列化的消息内容（JSON String）
     */
    public void publish(String topic, String messageJson) {
        try {
            // 注意：RabbitTemplate(String, String, Object) 签名中，第二个参数为空字符串表示使用默认/无路由键。
            // 适用于 Fanout 类型交换机或已绑定默认路由的 Topic 交换机。
            rabbitTemplate.convertAndSend(topic, "", messageJson);

            log.info("MQ任务补偿消息发送成功 | Topic: {}", topic);
        } catch (Exception e) {
            log.error("MQ任务补偿消息发送失败 | Topic: {} | Content: {}", topic, messageJson, e);
            throw e;
        }
    }
}