package com.c.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 基础设施配置：RabbitMQ 消息中间件
 * 1. 业务链路定义：负责库存售罄同步、奖品发放分发、行为返利投递三大核心业务的消息流转配置。
 * 2. 容错保障体系：构建死信交换机（DLX）机制，确保执行异常的消息能够进入补偿队列暂存。
 * 3. 序列化标准化：强制使用 JSON 格式替代 JDK 原生序列化，保障跨语言兼容性与可读性。
 *
 * @author cyh
 * @date 2026/02/05
 */
@Configuration
public class RabbitMQConfig {

    /** 死信交换机：用于接收所有业务队列中因重试耗尽或逻辑异常而失败的消息 */
    private static final String DLX_EXCHANGE = "dlx_exchange";
    /** 死信路由键：统一指向死信补偿队列 */
    private static final String DLX_ROUTING_KEY = "dlx_key";
    /** 公共死信队列：作为系统的“垃圾站”或“回收站”，由人工或 Job 介入处理 */
    private static final String DLX_QUEUE = "common_dead_letter_queue";

    // --- 属性注入：从配置文件（application.yml）动态加载 ---

    @Value("${spring.rabbitmq.topic.activity_sku_stock.exchange}")
    private String skuExchange;
    @Value("${spring.rabbitmq.topic.activity_sku_stock.queue}")
    private String skuQueue;
    @Value("${spring.rabbitmq.topic.activity_sku_stock.routing-key}")
    private String skuRoutingKey;

    @Value("${spring.rabbitmq.topic.send_award.exchange}")
    private String awardExchange;
    @Value("${spring.rabbitmq.topic.send_award.queue}")
    private String awardQueue;
    @Value("${spring.rabbitmq.topic.send_award.routing-key}")
    private String awardRoutingKey;

    @Value("${spring.rabbitmq.topic.send_rebate.exchange}")
    private String rebateExchange;
    @Value("${spring.rabbitmq.topic.send_rebate.queue}")
    private String rebateQueue;
    @Value("${spring.rabbitmq.topic.send_rebate.routing-key}")
    private String rebateRoutingKey;

    // --- 1. 库存售罄业务链路：用于秒杀场景下活动库存消耗的实时同步 ---

    @Bean
    public TopicExchange activitySkuStockExchange() {
        return new TopicExchange(skuExchange, true, false);
    }

    @Bean
    public Queue activitySkuStockQueue() {
        return QueueBuilder
                .durable(skuQueue)
                .deadLetterExchange(DLX_EXCHANGE)
                .deadLetterRoutingKey(DLX_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding bindingActivitySkuStock() {
        return BindingBuilder
                .bind(activitySkuStockQueue())
                .to(activitySkuStockExchange())
                .with(skuRoutingKey);
    }

    // --- 2. 发送奖品业务链路：解耦抽奖逻辑与奖品发放（发货）逻辑 ---

    @Bean
    public DirectExchange sendAwardExchange() {
        return new DirectExchange(awardExchange, true, false);
    }

    @Bean
    public Queue sendAwardQueue() {
        return QueueBuilder
                .durable(awardQueue)
                .deadLetterExchange(DLX_EXCHANGE)
                .deadLetterRoutingKey(DLX_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding bindingSendAward() {
        return BindingBuilder
                .bind(sendAwardQueue())
                .to(sendAwardExchange())
                .with(awardRoutingKey);
    }

    // --- 3. 发送返利业务链路：支撑签到、支付等行为后的营销返利发放 ---

    @Bean
    public TopicExchange sendRebateExchange() {
        return new TopicExchange(rebateExchange, true, false);
    }

    @Bean
    public Queue sendRebateQueue() {
        return QueueBuilder
                .durable(rebateQueue)
                .deadLetterExchange(DLX_EXCHANGE)
                .deadLetterRoutingKey(DLX_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding bindingSendRebate() {
        return BindingBuilder
                .bind(sendRebateQueue())
                .to(sendRebateExchange())
                .with(rebateRoutingKey);
    }

    // --- 4. 死信队列 (DLX) 保障体系：全系统的最后一道防线 ---

    @Bean
    public DirectExchange dlxExchange() {
        return new DirectExchange(DLX_EXCHANGE, true, false);
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder
                .durable(DLX_QUEUE)
                .build();
    }

    @Bean
    public Binding deadLetterBinding() {
        return BindingBuilder
                .bind(deadLetterQueue())
                .to(dlxExchange())
                .with(DLX_ROUTING_KEY);
    }

    // --- 5. 基础设施增强：统一序列化协议 ---

    /**
     * 定义 JSON 消息转换器
     * 使用 Jackson 替代 JDK 默认序列化，提升传输效率并解决跨服务传输的 Serializable 问题。
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}