package com.c.infrastructure.event;

import com.alibaba.fastjson.JSON;
import com.c.types.event.BaseEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 消息事件发布服务
 * 职责：
 * 1. 封装 Spring AMQP 消息发送逻辑，实现领域事件向消息中间件的平滑传输。
 * 2. 统一序列化标准，将领域层的事件消息对象（EventMessage）转化为持久化的 JSON 字符串。
 * 3. 提供统一的异常捕获与发送轨迹记录（Trace Log），支撑分布式链路监控。
 *
 * @author cyh
 * @date 2026/01/31
 */
@Slf4j
@Component
public class EventPublisher {

    @Resource
    private RabbitTemplate rabbitTemplate;

    /**
     * 发布领域事件消息
     * 业务逻辑：
     * 1. 基于 RabbitMQ 标准模型，通过指定的交换机（Exchange）与路由键（RoutingKey）进行定向分发。
     * 2. 消息体封装了全局唯一的 MessageID，用于下游消费端实现幂等校验。
     *
     * @param exchange     交换机名称，定义消息的分发策略（Direct/Topic/Fanout）
     * @param routingKey   路由键，用于将消息精确匹配至对应逻辑队列
     * @param eventMessage 事件消息包装对象，包含业务负载数据、消息 ID 及产生时间
     */
    public void publish(String exchange, String routingKey, BaseEvent.EventMessage<?> eventMessage) {
        try {
            // 1. 统一序列化：将事件负载转化为标准 JSON 字符串
            String messageJson = JSON.toJSONString(eventMessage);

            // 2. 消息投递：执行 RabbitMQ 转换并发送动作
            rabbitTemplate.convertAndSend(exchange, routingKey, messageJson);

            // 3. 审计日志：记录发送成功状态，包含消息 ID 以便进行端到端链路追溯
            log.info("发送 MQ 消息成功 Exchange:{} RoutingKey:{} MessageID:{}",
                    exchange, routingKey, eventMessage.getId());
        } catch (Exception e) {
            // 4. 故障感知：记录发送异常，并向调用方抛出异常以便触发上层事务回滚或重试补偿
            log.error("发送 MQ 消息失败 Exchange:{} RoutingKey:{} MessageID:{}",
                    exchange, routingKey, eventMessage.getId(), e);
            throw e;
        }
    }
}