package com.c.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 消息中间件配置类
 * <p>
 * 职责：
 * 1. 声明业务交换机（Exchange）、队列（Queue）及绑定关系（Binding）。
 * 2. 构建自动化基础设施：Spring 启动时会自动根据此配置在 RabbitMQ 中创建缺失的组件。
 * 3. 容错机制：配置死信队列（Dead Letter Exchange），确保异常消息可追溯、不丢失。
 * 4. 序列化标准：定义全量消息使用 JSON 格式传输，确保异构系统间的通信兼容性。
 */
@Configuration
public class RabbitMQConfig {

    // --- 库存相关属性注入 ---
    @Value("${spring.rabbitmq.topic.activity_sku_stock.exchange}")
    private String skuExchange;
    @Value("${spring.rabbitmq.topic.activity_sku_stock.queue}")
    private String skuQueue;
    @Value("${spring.rabbitmq.topic.activity_sku_stock.routing-key}")
    private String skuRoutingKey;

    // --- 发奖相关属性注入 ---
    @Value("${spring.rabbitmq.topic.send_award.exchange}")
    private String awardExchange;
    @Value("${spring.rabbitmq.topic.send_award.queue}")
    private String awardQueue;
    @Value("${spring.rabbitmq.topic.send_award.routing-key}")
    private String awardRoutingKey;

    // --- 1. 库存售罄业务链路配置 ---

    /**
     * 定义库存售罄 Topic 交换机
     * Topic 模式允许通过通配符进行消息分发，适合未来复杂的库存监控场景。
     */
    @Bean
    public TopicExchange activitySkuStockExchange() {
        return new TopicExchange(skuExchange, true, false);
    }

    /**
     * 定义库存售罄核心队列
     * 设置消息持久化（durable），并绑定死信交换机，当消费失败并拒绝时，消息将进入死信链路。
     */
    @Bean
    public Queue activitySkuStockQueue() {
        return QueueBuilder.durable(skuQueue)
                           .deadLetterExchange("dlx_exchange")
                           .deadLetterRoutingKey("dlx_key")
                           .build();
    }

    /**
     * 绑定库存交换机与队列
     */
    @Bean
    public Binding bindingActivitySkuStock() {
        return BindingBuilder.bind(activitySkuStockQueue()).to(activitySkuStockExchange()).with(skuRoutingKey);
    }

    // --- 2. 发送奖品业务链路配置 (对接 Repository 任务表) ---

    /**
     * 定义发送奖品 Direct 交换机
     * Direct 模式提供点对点的精确投递，确保奖品发放指令准确到达奖品服务。
     */
    @Bean
    public DirectExchange sendAwardExchange() {
        return new DirectExchange(awardExchange, true, false);
    }

    /**
     * 定义发送奖品队列
     */
    @Bean
    public Queue sendAwardQueue() {
        return QueueBuilder.durable(awardQueue)
                           .deadLetterExchange("dlx_exchange")
                           .deadLetterRoutingKey("dlx_key")
                           .build();
    }

    /**
     * 绑定发送奖品链路
     */
    @Bean
    public Binding bindingSendAward() {
        return BindingBuilder.bind(sendAwardQueue()).to(sendAwardExchange()).with(awardRoutingKey);
    }

    // --- 3. 死信队列 (DLX) 保障体系 ---

    /**
     * 声明通用死信交换机
     * 职责：作为所有异常消息的“中转站”。
     */
    @Bean
    public DirectExchange dlxExchange() {
        return new DirectExchange("dlx_exchange");
    }

    /**
     * 声明通用死信队列
     * 职责：物理落地存储异常消息，用于后期人工介入排查或自动化重试补偿。
     */
    @Bean
    public Queue deadLetterQueue() {
        return new Queue("common_dead_letter_queue", true);
    }

    /**
     * 绑定死信链路
     */
    @Bean
    public Binding deadLetterBinding() {
        return BindingBuilder.bind(deadLetterQueue()).to(dlxExchange()).with("dlx_key");
    }

    // --- 4. 基础设施增强 ---

    /**
     * 配置 Jackson 消息转换器
     * 为什么必须配置：
     * 1. 默认使用的是 JDK 序列化，会导致 RabbitMQ 管理后台看到的是二进制乱码。
     * 2. 使用 JSON 转换器后，RabbitTemplate 会自动将对象转为 JSON 字符串，并设置 content_type 为 application/json。
     * 3. 配合 EventPublisher，解决了手动调用 JSON.toJSONString 的重复代码问题。
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}