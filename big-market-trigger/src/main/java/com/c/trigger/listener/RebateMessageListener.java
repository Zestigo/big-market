package com.c.trigger.listener;

import com.c.domain.activity.model.entity.SkuRechargeEntity;
import com.c.domain.activity.service.IRaffleActivityAccountQuotaService;
import com.c.domain.rebate.event.SendRebateMessageEvent;
import com.c.domain.rebate.model.vo.RebateTypeVO;
import com.c.types.enums.ResponseCode;
import com.c.types.event.BaseEvent;
import com.c.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 消息监听器：用户行为返利转换与入账
 */
@Slf4j
@Component
public class RebateMessageListener {

    @Value("${spring.rabbitmq.topic.send_rebate.exchange}")
    private String exchange;

    @Resource
    private IRaffleActivityAccountQuotaService raffleActivityAccountQuotaService;

    /**
     * 消费并处理返利消息
     * 修改点：将入参从 String 改为具体的 EventMessage 泛型对象，由 Spring MessageConverter 自动完成解析
     */
    @RabbitListener(queues = "${spring.rabbitmq.topic.send_rebate.queue}")
    public void onMessage(BaseEvent.EventMessage<SendRebateMessageEvent.RebateMessage> eventMessage) {
        // [1] 防御性检查
        if (null == eventMessage || null == eventMessage.getData()) {
            log.warn("【警告】接收到无效返利消息，放弃处理 | Exchange: {}", exchange);
            return;
        }

        SendRebateMessageEvent.RebateMessage rebateMessage = eventMessage.getData();
        String bizId = rebateMessage.getBizId();

        try {
            log.info("【返利消费开始】业务标识: {} | 用户ID: {} | 消息时间: {}", bizId, rebateMessage.getUserId(),
                    eventMessage.getTimestamp());

            // [2] 类型过滤：仅处理 SKU 类型返利
            if (!RebateTypeVO.SKU
                    .getCode()
                    .equals(rebateMessage.getRebateType())) {
                log.info("【任务跳过】该返利非 SKU 类型，无需入账活动额度 | 业务标识: {} | 类型: {}", bizId, rebateMessage.getRebateType());
                return;
            }

            // [3] 转换领域实体
            SkuRechargeEntity skuRechargeEntity = SkuRechargeEntity
                    .builder()
                    .userId(rebateMessage.getUserId())
                    .sku(Long.valueOf(rebateMessage.getRebateConfig()))
                    .outBusinessNo(bizId)
                    .build();

            // [4] 驱动领域服务：创建充值订单
            raffleActivityAccountQuotaService.createOrder(skuRechargeEntity);

            log.info("【返利消费成功】额度入账完成 | 业务标识: {} | SKU: {}", bizId, rebateMessage.getRebateConfig());

        } catch (AppException e) {
            // 幂等处理：如果是唯一索引冲突，说明已经入账成功了
            if (ResponseCode.INDEX_DUP
                    .getCode()
                    .equals(e.getCode())) {
                log.warn("【拦截】发现重复消费记录（幂等生效） | 业务标识: {}", bizId);
                return;
            }
            log.error("【业务异常】返利入账失败 | 业务标识: {}", bizId, e);
            throw e; // 抛出异常触发 MQ 重试
        } catch (Exception e) {
            log.error("【系统异常】返利消费链路崩溃 | 业务标识: {}", bizId, e);
            throw e; // 抛出异常触发 MQ 重试
        }
    }
}