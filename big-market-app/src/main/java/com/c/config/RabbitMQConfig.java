package com.c.config;

import org.springframework.amqp.core.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 基础设施配置：RabbitMQ 消息中间件
 * 包含：库存售罄、奖品发放、营销返利、积分调账及死信队列
 *
 * @author cyh
 * @date 2026/02/15
 */
@Configuration
public class RabbitMQConfig {

    /* 死信交换机名称 */
    private static final String DLX_EXCHANGE = "dlx_exchange";
    /* 死信路由键 */
    private static final String DLX_ROUTING_KEY = "dlx_key";
    /* 死信队列名称 */
    private static final String DLX_QUEUE = "common_dead_letter_queue";

    @Value("${spring.rabbitmq.topic.activity_sku_stock.exchange}")
    private String skuExchange; /* SKU库存交换机 */
    @Value("${spring.rabbitmq.topic.activity_sku_stock.queue}")
    private String skuQueue; /* SKU库存队列 */
    @Value("${spring.rabbitmq.topic.activity_sku_stock.routing-key}")
    private String skuRoutingKey; /* SKU库存路由键 */

    @Value("${spring.rabbitmq.topic.send_award.exchange}")
    private String awardExchange; /* 奖品发放交换机 */
    @Value("${spring.rabbitmq.topic.send_award.queue}")
    private String awardQueue; /* 奖品发放队列 */
    @Value("${spring.rabbitmq.topic.send_award.routing-key}")
    private String awardRoutingKey; /* 奖品发放路由键 */

    @Value("${spring.rabbitmq.topic.send_rebate.exchange}")
    private String rebateExchange; /* 营销返利交换机 */
    @Value("${spring.rabbitmq.topic.send_rebate.queue}")
    private String rebateQueue; /* 营销返利队列 */
    @Value("${spring.rabbitmq.topic.send_rebate.routing-key}")
    private String rebateRoutingKey; /* 营销返利路由键 */

    @Value("${spring.rabbitmq.topic.credit_adjust_success.exchange}")
    private String creditExchange; /* 积分调账交换机 */
    @Value("${spring.rabbitmq.topic.credit_adjust_success.queue}")
    private String creditQueue; /* 积分调账队列 */
    @Value("${spring.rabbitmq.topic.credit_adjust_success.routing-key}")
    private String creditRoutingKey; /* 积分调账路由键 */

    // --- 1. 库存售罄链路 (Topic) ---

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
    public Binding bindingActivitySkuStock(Queue activitySkuStockQueue, TopicExchange activitySkuStockExchange) {
        return BindingBuilder
                .bind(activitySkuStockQueue)
                .to(activitySkuStockExchange)
                .with(skuRoutingKey);
    }

    // --- 2. 奖品发放链路 (Direct) ---

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
    public Binding bindingSendAward(Queue sendAwardQueue, DirectExchange sendAwardExchange) {
        return BindingBuilder
                .bind(sendAwardQueue)
                .to(sendAwardExchange)
                .with(awardRoutingKey);
    }

    // --- 3. 营销返利链路 (Topic) ---

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
    public Binding bindingSendRebate(Queue sendRebateQueue, TopicExchange sendRebateExchange) {
        return BindingBuilder
                .bind(sendRebateQueue)
                .to(sendRebateExchange)
                .with(rebateRoutingKey);
    }

    // --- 4. 积分调账链路 (Topic) ---

    @Bean
    public TopicExchange creditExchange() {
        return new TopicExchange(creditExchange, true, false);
    }

    @Bean
    public Queue creditQueue() {
        return QueueBuilder
                .durable(creditQueue)
                .deadLetterExchange(DLX_EXCHANGE)
                .deadLetterRoutingKey(DLX_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding bindingCreditAdjust(Queue creditQueue, TopicExchange creditExchange) {
        return BindingBuilder
                .bind(creditQueue)
                .to(creditExchange)
                .with(creditRoutingKey);
    }

    // --- 5. 死信保障体系 (DLX) ---

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
    public Binding deadLetterBinding(Queue deadLetterQueue, DirectExchange dlxExchange) {
        return BindingBuilder
                .bind(deadLetterQueue)
                .to(dlxExchange)
                .with(DLX_ROUTING_KEY);
    }
}