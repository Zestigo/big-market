package com.c.domain.credit.event;

import com.c.types.event.BaseEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 积分调减/额度调整成功消息事件
 * 负责构建积分账户变动成功的领域消息，并提供对应的交换机与路由键配置。
 *
 * @author cyh
 * @date 2026/02/09
 */
@Component
public class CreditAdjustSuccessMessageEvent extends BaseEvent<CreditAdjustSuccessMessageEvent.CreditAdjustSuccessMessage> {

    @Value("${spring.rabbitmq.topic.credit_adjust_success.exchange}")
    private String exchange;

    @Value("${spring.rabbitmq.topic.credit_adjust_success.routing-key}")
    private String routingKey;

    @Override
    public EventMessage<CreditAdjustSuccessMessage> buildEventMessage(CreditAdjustSuccessMessage data) {
        return EventMessage
                .<CreditAdjustSuccessMessage>builder()
                .id(RandomStringUtils.randomNumeric(11))
                .timestamp(new Date())
                .data(data)
                .build();
    }

    @Override
    public String exchange() {
        return exchange;
    }

    @Override
    public String routingKey() {
        return routingKey;
    }

    /**
     * 积分调整成功消息体定义
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CreditAdjustSuccessMessage {

        /** 用户ID */
        private String userId;

        /** 订单ID */
        private String orderId;

        /** 交易金额 */
        private BigDecimal amount;

        /** 业务仿重ID，外部透传的唯一标识（如返利、行为标识） */
        private String outBusinessNo;
    }

}