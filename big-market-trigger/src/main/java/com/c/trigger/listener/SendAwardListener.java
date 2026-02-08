package com.c.trigger.listener;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.c.domain.award.event.SendAwardMessageEvent;
import com.c.domain.award.model.entity.DistributeAwardEntity;
import com.c.domain.award.service.IAwardService;
import com.c.types.event.BaseEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 抽奖发奖消息监听器
 * 职责：响应抽奖成功事件，异步触发奖品发放流程，实现抽奖与发奖的解耦。
 *
 * @author cyh
 * @date 2026/02/05
 */
@Slf4j
@Component
public class SendAwardListener {

    @Value("${spring.rabbitmq.topic.send_award.exchange}")
    private String exchange;

    @Resource
    private IAwardService awardService;

    /**
     * 监听并发放奖品
     * 1. 转换：解析消息体封装为 DistributeAwardEntity 领域对象。
     * 2. 执行：调用 awardService 进行奖品发放（涉及账户更新、物流开单等）。
     * 3. 容错：若发放失败，抛出异常触发 MQ 重试机制，确保奖品最终送达。
     *
     * @param message 原始 JSON 消息字符串
     */
    @RabbitListener(queues = "${spring.rabbitmq.topic.send_award.queue}")
    public void onMessage(String message) {
        if (StringUtils.isBlank(message)) {
            log.warn("接收到空值发奖消息，跳过处理 | Exchange: {}", exchange);
            return;
        }

        try {
            log.info("监听用户奖品发送消息 exchange: {} message: {}", exchange, message);

            // 1. 消息解析
            BaseEvent.EventMessage<SendAwardMessageEvent.SendAwardMessage> eventMessage = JSON.parseObject(message,
                    new TypeReference<BaseEvent.EventMessage<SendAwardMessageEvent.SendAwardMessage>>() {
                    }.getType());
            SendAwardMessageEvent.SendAwardMessage sendAwardMessage = eventMessage.getData();

            // 2. 构造分发实体
            DistributeAwardEntity distributeAwardEntity = DistributeAwardEntity
                    .builder()
                    .userId(sendAwardMessage.getUserId())
                    .orderId(sendAwardMessage.getOrderId())
                    .awardId(sendAwardMessage.getAwardId())
                    .awardConfig(sendAwardMessage.getAwardConfig())
                    .build();

            // 3. 执行发放逻辑
            awardService.distributeAward(distributeAwardEntity);

        } catch (Exception e) {
            // 捕获异常需重新抛出，以便触发 RabbitMQ 的重试或进入死信队列
            log.error("执行奖品发放失败，触发重试 | 消息内容: {}", message, e);
            throw e;
        }
    }
}