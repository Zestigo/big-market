package com.c.domain.activity.event;

import com.c.types.event.BaseEvent;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * 活动 SKU 库存售罄领域事件
 * 职责：
 * 1. 定义库存清零事件的业务标准，包含消息结构与分发契约（交换机与路由键）。
 * 2. 负责构建标准的事件消息载体，生成用于幂等校验的全局唯一事件 ID。
 * 3. 作为领域层向基础设施层传递信号的标准化对象。
 *
 * @author cyh
 * @date 2026/01/31
 */
@Component
public class ActivitySkuStockZeroMessageEvent extends BaseEvent<Long> {

    @Value("${spring.rabbitmq.topic.activity_sku_stock.exchange}")
    private String exchange;

    @Value("${spring.rabbitmq.topic.activity_sku_stock.routing-key}")
    private String routingKey;

    /**
     * 构建库存售罄事件消息体
     * 业务逻辑：
     * 1. 自动生成 11 位数字格式的唯一消息 ID，作为消费端防重幂等的关键凭证。
     * 2. 记录事件产生的精确时间戳，便于后续链路耗时监控与审计。
     * 3. 承载受影响的 SKU 标识作为事件核心负载。
     *
     * @param sku 触发售罄状态的活动商品唯一标识
     * @return 标准化的事件消息包装对象 {@link EventMessage}
     */
    @Override
    public EventMessage<Long> buildEventMessage(Long sku) {
        return EventMessage.<Long>builder().id(RandomStringUtils.randomNumeric(11)).timestamp(new Date())
                           .data(sku).build();
    }

    /**
     * 获取事件关联的交换机名称
     * 对应 RabbitMQ 中的 Exchange，定义了消息的分发范围。
     */
    @Override
    public String topic() {
        return exchange;
    }

    /**
     * 获取事件关联的路由键
     * 对应 RabbitMQ 中的 RoutingKey，决定了消息在交换机内精准投递的逻辑。
     */
    public String routingKey() {
        return routingKey;
    }
}