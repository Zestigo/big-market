package com.c.domain.activity.event;

import com.c.types.event.BaseEvent;
import org.apache.commons.lang.RandomStringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * 活动 SKU 库存售罄领域事件
 * * 业务场景：当活动商品库存扣减至零时由领域服务触发，用于通知下游系统执行同步操作。
 * 契约规范：定义了库存清零消息的交换机（Exchange）与路由键（Routing Key）匹配规则。
 * 核心逻辑：解耦库存扣减业务与后续的缓存刷新、搜索索引下架等周边逻辑。
 * * @author cyh
 *
 * @date 2026/01/31
 */
@Component
public class ActivitySkuStockZeroMessageEvent extends BaseEvent<Long> {

    /** 消息队列交换机名称：对应配置中的活动库存业务域 */
    @Value("${spring.rabbitmq.topic.activity_sku_stock.exchange}")
    private String exchange;

    /** 消息队列路由键：对应配置中的售罄消息分发路径 */
    @Value("${spring.rabbitmq.topic.activity_sku_stock.routing-key}")
    private String routingKey;

    /**
     * 构建库存售罄事件的标准消息载体
     * * 1. 幂等设计：生成 11 位全局唯一流水号，支撑消费端执行幂等去重校验。
     * 2. 负载信息：Data 字段承载售罄的具体 SKU 编号。
     * 3. 审计支持：记录 timestamp 用于监控从库存售罄到消息触达的端到端耗时。
     * * @param sku 触发售罄状态的商品唯一标识
     *
     * @return 包含唯一标识与业务负载的标准事件包装对象
     */
    @Override
    public EventMessage<Long> buildEventMessage(Long sku) {
        return EventMessage.<Long>builder().id(RandomStringUtils.randomNumeric(11)) // 生成唯一幂等 ID
                           .timestamp(new Date())                   // 记录事件产生时间
                           .data(sku)                               // 设置业务负载数据
                           .build();
    }

    /**
     * 获取事件关联的交换机名称 (Exchange)
     * 对应 RabbitMQ 中的路由入口，决定消息的初步分发范围。
     */
    @Override
    public String exchange() {
        return exchange;
    }

    /**
     * 获取事件关联的路由键 (Routing Key)
     * 配合 Topic 模式实现消息的精准分拣，决定消息最终进入哪个队列。
     */
    @Override
    public String routingKey() {
        return routingKey;
    }
}