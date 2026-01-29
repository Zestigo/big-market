package com.c.types.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * @author cyh
 * @description 基础事件抽象类
 * 规范了所有领域事件的共有属性和行为。通过泛型 T 适配不同的业务数据负载。
 * @date 2026/01/28
 */
@Data
public abstract class BaseEvent<T> {

    /**
     * 构造事件消息体
     *
     * @param data 具体的业务数据（负载）
     * @return 封装好的标准事件消息对象
     */
    public abstract EventMessage<T> buildEventMessage(T data);

    /**
     * 获取该事件对应的消息队列主题（Topic/Exchange）
     */
    public abstract String topic();

    /**
     * @description 标准事件消息包裹对象
     * 无论什么业务事件，在传输时都会统一封装成这个结构，便于消费者进行通用的幂等处理和日志追踪。
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class EventMessage<T> {
        /** 消息唯一标识（通常使用 UUID 或分布式 ID，用于消费者幂等校验） */
        private String id;

        /** 消息发生的时间戳（用于排查业务发生的先后顺序） */
        private Date timestamp;

        /** 业务数据负载（具体的 SKU、订单 ID 或其他实体对象） */
        private T data;
    }

}