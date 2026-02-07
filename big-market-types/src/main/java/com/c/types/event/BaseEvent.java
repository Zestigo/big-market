package com.c.types.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 基础事件抽象父类
 * * 职责：
 * 1. 契约定义：规范了所有领域事件必须具备的路由属性（Exchange, RoutingKey）。
 * 2. 结构标准化：强制要求通过泛型 T 适配业务数据，并统一封装为标准消息格式。
 * 3. 链路追踪：通过 EventMessage 支撑全局唯一 ID 和时间戳，便于消息回溯。
 *
 * @param <T> 领域事件携带的业务负载类型
 */
@Data
public abstract class BaseEvent<T> {

    /**
     * 构建标准事件消息包裹对象
     * * @param data 业务实体负载（如 Sku, Order 等）
     * @return 包含元数据的标准消息对象
     */
    public abstract EventMessage<T> buildEventMessage(T data);

    /**
     * 获取该事件绑定的交换机名称 (Exchange)
     * 对应 RabbitMQ 中的路由入口。
     */
    public abstract String exchange();

    /**
     * 获取该事件绑定的路由键 (Routing Key)
     * 决定消息在交换机内的分发策略。
     */
    public abstract String routingKey();

    /**
     * 标准事件消息载体
     * * 作用：统一消息协议，承载幂等 ID 与时间戳，用于消费者去重与日志追踪。
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class EventMessage<T> {
        /** 消息唯一标识：建议使用 UUID 或雪花算法，用于消费端执行强幂等校验 */
        private String id;

        /** 事件发生时间：用于监控消息积压及链路时序审计 */
        private Date timestamp;

        /** 业务负载数据：具体的领域模型实体 */
        private T data;
    }
}