package com.c.domain.rebate.event;

import com.c.types.event.BaseEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * 领域事件：返利消息发放
 * * 职责：定义行为返利（如签到、分享）产生后的标准化信号，驱动下游账户加钱或物流系统发货。
 * 契约：封装了 RabbitMQ 的交换机 (Exchange) 与路由键 (Routing Key) 匹配规则，支撑可靠消息传递。
 *
 * @author cyh
 * @date 2026/02/05
 */
@Component
public class SendRebateMessageEvent extends BaseEvent<SendRebateMessageEvent.RebateMessage> {

    /** 消息队列交换机名称：对应配置中定义的返利业务域交换机 */
    @Value("${spring.rabbitmq.topic.send_rebate.exchange}")
    private String exchange;

    /** 消息队列路由键：对应配置中定义的返利分发路由规则 */
    @Value("${spring.rabbitmq.topic.send_rebate.routing-key}")
    private String routingKey;

    /**
     * 构建发送返利消息的标准包装对象
     * 1. 幂等生成：自动产生 11 位数字流水 ID，用于生产端 Confirm 与消费端去重。
     * 2. 时间戳记录：精准记录事件产生时刻，支撑全链路追踪。
     * 3. 数据隔离：将业务载荷 (RebateMessage) 封装在标准 EventMessage 中。
     *
     * @param data 返利消息业务数据主体
     * @return 包含元数据与业务载荷的标准事件对象
     */
    @Override
    public EventMessage<RebateMessage> buildEventMessage(RebateMessage data) {
        return EventMessage
                .<SendRebateMessageEvent.RebateMessage>builder()
                .id(RandomStringUtils.randomNumeric(11))
                .timestamp(new Date())
                .data(data)
                .build();
    }

    /**
     * 获取事件关联的消息队列交换机 (Exchange)
     */
    @Override
    public String exchange() {
        return exchange;
    }

    /**
     * 获取事件发送所需的路由键 (Routing Key)
     */
    @Override
    public String routingKey() {
        return routingKey;
    }

    /**
     * 内部类：返利消息业务载荷 (Payload)
     * 描述具体的返利执行要素，确保消费端拥有足够的上下文进行业务处理。
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RebateMessage {

        /** 用户唯一标识 ID：指明返利归属者 */
        private String userId;

        /** 返利业务行为描述：如“每日签到返利”、“邀请好友奖励” */
        private String rebateDesc;

        /** 返利奖励类型：sku (实物/虚拟商品)、integral (积分) */
        private String rebateType;

        /** 返利配置具体值：具体的商品 ID 或积分数值 */
        private String rebateConfig;

        /** 业务唯一防重 ID：关联原始业务单据，用于消费端执行强幂等校验 */
        private String bizId;

    }

}