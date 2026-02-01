package com.c.config;

import org.springframework.amqp.core.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 消息中间件配置类
 * 职责：
 * 1. 构建活动 SKU 库存售罄的可靠消息传输链路（Exchange, Queue, Binding）。
 * 2. 建立高可用死信兜底机制，确保消费失败的消息可追踪、可补偿。
 *
 * @author cyh
 * @date 2026/01/31
 */
@Configuration
public class RabbitMQConfig {

    @Value("${spring.rabbitmq.topic.activity_sku_stock.exchange}")
    private String exchangeName;

    @Value("${spring.rabbitmq.topic.activity_sku_stock.queue}")
    private String queueName;

    @Value("${spring.rabbitmq.topic.activity_sku_stock.routing-key}")
    private String routingKey;

    /**
     * 定义业务 Topic 交换机
     * 采用 Topic 模式以支持后续针对不同 SKU 类型或层级的路由扩展。
     */
    @Bean
    public TopicExchange activitySkuStockZeroExchange() {
        return new TopicExchange(exchangeName, true, false);
    }

    /**
     * 定义核心业务队列
     * 配置 x-dead-letter 属性，将处理失败（NACK）且重试耗尽的消息转发至死信链路。
     */
    @Bean
    public Queue activitySkuStockZeroQueue() {
        return QueueBuilder.durable(queueName).deadLetterExchange("dlx_exchange") // 绑定死信交换机
                           .deadLetterRoutingKey("dlx_key")    // 绑定死信路由键
                           .build();
    }

    /**
     * 建立业务路由绑定
     * 将交换机与队列通过路由键（Routing Key）关联。
     */
    @Bean
    public Binding bindingActivitySkuStockZero() {
        return BindingBuilder.bind(activitySkuStockZeroQueue()).to(activitySkuStockZeroExchange())
                             .with(routingKey);
    }

    // --- 死信队列保障体系 ---

    /**
     * 定义死信交换机 (DLX)
     * 专门用于接收从业务队列退信（Rejected/Expired）的消息。
     */
    @Bean
    public DirectExchange dlxExchange() {
        return new DirectExchange("dlx_exchange");
    }

    /**
     * 定义死信队列 (DLQ)
     * 存放最终失败的消息，用于日志审计、人工修复或自动化补单。
     */
    @Bean
    public Queue deadLetterQueue() {
        return new Queue("activity_sku_stock_zero_dlq", true);
    }

    /**
     * 绑定死信链路
     * 明确死信交换机与死信队列的映射关系。
     */
    @Bean
    public Binding deadLetterBinding() {
        return BindingBuilder.bind(deadLetterQueue()).to(dlxExchange()).with("dlx_key");
    }
}