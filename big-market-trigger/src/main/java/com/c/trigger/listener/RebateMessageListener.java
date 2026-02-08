package com.c.trigger.listener;

import com.c.domain.activity.model.entity.SkuRechargeEntity;
import com.c.domain.activity.service.IRaffleActivityAccountQuotaService;
import com.c.domain.credit.model.entity.TradeEntity;
import com.c.domain.credit.model.vo.TradeNameVO;
import com.c.domain.credit.model.vo.TradeTypeVO;
import com.c.domain.credit.service.ICreditAdjustService;
import com.c.domain.rebate.event.SendRebateMessageEvent;
import com.c.types.enums.ResponseCode;
import com.c.types.event.BaseEvent;
import com.c.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.math.BigDecimal;

/**
 * 消息监听器：用户行为返利转换与入账
 * 职责：转换返利消息为具体的业务入账（活动额度/积分账户）
 *
 * @author cyh
 * @date 2026/02/08
 */
@Slf4j
@Component
public class RebateMessageListener {

    @Resource
    private IRaffleActivityAccountQuotaService raffleActivityAccountQuotaService;
    @Resource
    private ICreditAdjustService creditAdjustService;

    @RabbitListener(queues = "${spring.rabbitmq.topic.send_rebate.queue}")
    public void onMessage(BaseEvent.EventMessage<SendRebateMessageEvent.RebateMessage> eventMessage) {
        // [1] 防御性检查
        if (null == eventMessage || null == eventMessage.getData()) {
            log.error("【MQ消费】接收到无效返利消息，直接丢弃");
            return;
        }

        SendRebateMessageEvent.RebateMessage rebateMessage = eventMessage.getData();
        String bizId = rebateMessage.getBizId();

        try {
            log.info("【返利消费】开始处理消息 bizId:{} userId:{} type:{}", bizId, rebateMessage.getUserId(),
                    rebateMessage.getRebateType());

            // [2] 业务分发逻辑
            switch (rebateMessage.getRebateType()) {
                case "sku":
                    // 转换：行为返利 -> 活动额度入账
                    raffleActivityAccountQuotaService.createOrder(SkuRechargeEntity
                            .builder()
                            .userId(rebateMessage.getUserId())
                            .sku(Long.valueOf(rebateMessage.getRebateConfig()))
                            .outBusinessNo(bizId)
                            .build());
                    break;
                case "integral":
                    // 转换：行为返利 -> 积分入账
                    creditAdjustService.createOrder(TradeEntity
                            .builder()
                            .userId(rebateMessage.getUserId())
                            .tradeName(TradeNameVO.REBATE)
                            .tradeType(TradeTypeVO.FORWARD)
                            .tradeAmount(new BigDecimal(rebateMessage.getRebateConfig()))
                            .outBusinessNo(bizId)
                            .build());
                    break;
                default:
                    log.warn("【返利消费】未定义的返利类型，暂不处理 bizId:{} type:{}", bizId, rebateMessage.getRebateType());
                    break;
            }

            log.info("【返利消费】处理成功 bizId:{}", bizId);
        } catch (AppException e) {
            // [3] 业务幂等拦截：底层 Dao 唯一索引冲突抛出的异常
            if (ResponseCode.INDEX_DUP
                    .getCode()
                    .equals(e.getCode())) {
                log.warn("【返利消费】幂等拦截，该笔返利已入账成功 bizId:{}", bizId);
                return;
            }
            log.error("【返利消费】业务处理失败，稍后重试 bizId:{}", bizId, e);
            throw e;
        } catch (Exception e) {
            log.error("【返利消费】系统异常，进入重试队列 bizId:{}", bizId, e);
            throw e;
        }
    }
}