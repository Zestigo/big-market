package com.c.trigger.listener;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.c.domain.award.event.SendAwardMessageEvent;
import com.c.domain.award.service.IAwardService;
import com.c.types.event.BaseEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 消息监听器：中奖结果分发与奖品发放
 * 1. 异步解耦：响应抽奖引擎发出的中奖指令，确保抽奖核心流程不被复杂的发奖逻辑阻塞。
 * 2. 消息可靠消费：通过 MQ 保证“发奖”动作最终一定执行，实现中奖结果的最终一致性。
 * 3. 业务路由：解析中奖信息，驱动奖品领域服务执行不同类型（积分、实物、优惠券）的发放动作。
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

    /** 静态化类型引用：解析中奖事件消息体 */
    private static final TypeReference<BaseEvent.EventMessage<SendAwardMessageEvent.SendAwardMessage>> MESSAGE_TYPE =
            new TypeReference<BaseEvent.EventMessage<SendAwardMessageEvent.SendAwardMessage>>() {
            };

    /**
     * 监听并消费发奖指令消息
     * 1. 消息解析：将 JSON 字符串还原为奖品分发所需的元数据实体。
     * 2. 领域驱动：调用 awardService 执行奖品配送（如：插入发奖记录、调用第三方接口）。
     * 3. 结果追踪：记录发奖轨迹，为后续用户查看“我的奖品”提供数据支撑。
     *
     * @param message 原始 JSON 消息字符串
     */
    @RabbitListener(queues = "${spring.rabbitmq.topic.send_award.queue}")
    public void onMessage(String message) {
        if (StringUtils.isBlank(message)) {
            log.warn("【警告】接收到空值发奖指令，放弃处理 | Exchange: {}", exchange);
            return;
        }

        try {
            // [步骤 1] 消息结构解析
            BaseEvent.EventMessage<SendAwardMessageEvent.SendAwardMessage> eventMessage = JSON.parseObject(message,
                    MESSAGE_TYPE);
            SendAwardMessageEvent.SendAwardMessage sendAwardMessage = eventMessage.getData();

            if (null == sendAwardMessage) {
                log.error("【错误】发奖消息载体缺失 | Message: {}", message);
                return;
            }

            // TODO

        } catch (Exception e) {
            // [步骤 4] 捕获异常触发重试：发奖失败必须重试，确保奖品不漏发
            log.error("【系统异常】执行奖品发放失败 | 消息内容: {}", message, e);
            throw e;
        }
    }
}