package com.c.infrastructure.event;

import com.c.types.event.BaseEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 消息事件发布服务
 * * 职责：作为基础设施层的统一消息出口，负责将领域事件可靠地投递至 RabbitMQ。
 * 特性：
 * 1. 路由精准：支持 Exchange + RoutingKey 的显式指定。
 * 2. 生产确认：配合 CorrelationData 实现 RabbitMQ 的 Publisher Confirm 机制，确保发送可追踪。
 * 3. 容错设计：提供针对 JSON 字符串（补偿场景）和 EventMessage（实时场景）的重载方法。
 *
 * @author cyh
 * @date 2026/02/05
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventPublisher {

    /** RabbitMQ 发送模板，需配置 Jackson2JsonMessageConverter 以支持对象自动序列化 */
    private final RabbitTemplate rabbitTemplate;

    /**
     * 发布 JSON 文本消息
     * 场景：主要用于任务补偿 Job 从数据库读取已序列化的 Task 报文进行重发。
     *
     * @param exchange    交换机名称
     * @param routingKey  路由键
     * @param messageJson 已经过 JSON 序列化的消息主体
     */
    public void publish(String exchange, String routingKey, String messageJson) {
        try {
            Objects.requireNonNull(exchange, "Exchange cannot be null");
            Objects.requireNonNull(routingKey, "RoutingKey cannot be null");

            rabbitTemplate.convertAndSend(exchange, routingKey, messageJson);

            log.info("MQ任务补偿消息发送成功 | Exchange: {} | RoutingKey: {}", exchange, routingKey);
        } catch (Exception e) {
            log.error("MQ任务补偿消息发送失败 | Exchange: {} | RoutingKey: {} | Message: {}", exchange, routingKey, messageJson
                    , e);
            throw e;
        }
    }

    /**
     * 发布标准领域事件对象
     * 场景：业务流程中产生领域事件后，通过此方法进行实时异步解耦。
     *
     * @param exchange     交换机名称
     * @param routingKey   路由键
     * @param eventMessage 包含元数据的标准事件包装对象
     */
    public void publish(String exchange, String routingKey, BaseEvent.EventMessage<?> eventMessage) {
        if (eventMessage == null) {
            log.warn("MQ投递取消：消息载体为空 | Exchange: {} | RoutingKey: {}", exchange, routingKey);
            return;
        }

        try {
            Objects.requireNonNull(exchange, "Exchange cannot be null");
            Objects.requireNonNull(routingKey, "RoutingKey cannot be null");

            // 构建 CorrelationData 以支撑生产端确认机制（ConfirmCallback）
            CorrelationData correlationData = new CorrelationData(eventMessage.getId());

            // 执行消息投递
            rabbitTemplate.convertAndSend(exchange, routingKey, eventMessage, correlationData);

            log.info("MQ消息发送成功 | ID: {} | Exchange: {} | RoutingKey: {}", eventMessage.getId(), exchange, routingKey);
        } catch (Exception e) {
            log.error("MQ消息发送异常 | ID: {} | Exchange: {} | RoutingKey: {} | Error: {}", eventMessage.getId(), exchange
                    , routingKey, e.getMessage(), e);
            throw new RuntimeException("RabbitMQ message publishing failed", e);
        }
    }
}