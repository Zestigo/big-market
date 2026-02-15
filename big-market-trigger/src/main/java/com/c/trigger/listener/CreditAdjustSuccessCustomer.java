package com.c.trigger.listener;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.c.domain.activity.model.entity.DeliveryOrderEntity;
import com.c.domain.activity.service.IRaffleActivityAccountQuotaService;
import com.c.domain.credit.event.CreditAdjustSuccessMessageEvent;
import com.c.types.enums.ResponseCode;
import com.c.types.event.BaseEvent;
import com.c.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 积分调账成功消息监听器
 *
 * @author cyh
 * @date 2026/02/09
 */
@Slf4j
@Component
public class CreditAdjustSuccessCustomer {

    @Value("${spring.rabbitmq.topic.credit_adjust_success.exchange}")
    private String exchange; /* 监听的交换机名称 */

    @Resource
    private IRaffleActivityAccountQuotaService raffleActivityAccountQuotaService; /* 额度服务 */

    @RabbitListener(queues = "${spring.rabbitmq.topic.credit_adjust_success.queue}")
    public void listener(String message) {
        /* 强制打印到控制台，确保即便日志级别有问题也能看到 */
        System.out.println(">>>>>> [DEBUG] 收到消息了！！内容是: " + message);

        try {
            log.info("监听积分账户调整成功消息 exchange: {} message: {}", exchange, message);

            // 1. 手动解析消息
            BaseEvent.EventMessage<CreditAdjustSuccessMessageEvent.CreditAdjustSuccessMessage> eventMessage =
                    JSON.parseObject(message,
                            new TypeReference<BaseEvent.EventMessage<CreditAdjustSuccessMessageEvent.CreditAdjustSuccessMessage>>() {
                            }.getType());

            // 2. 提取载体数据
            CreditAdjustSuccessMessageEvent.CreditAdjustSuccessMessage data = eventMessage.getData();

            // 3. 构建发货单实体
            DeliveryOrderEntity deliveryOrderEntity = DeliveryOrderEntity
                    .builder()
                    .userId(data.getUserId())
                    .outBusinessNo(data.getOutBusinessNo())
                    .build();

            // 4. 执行发货（更新活动额度）
            raffleActivityAccountQuotaService.updateOrder(deliveryOrderEntity);

        } catch (AppException e) {
            /* 幂等拦截：数据库唯一索引冲突，视为已消费成功 */
            if (ResponseCode.INDEX_DUP
                    .getCode()
                    .equals(e.getCode())) {
                log.warn("监听积分账户调整成功消息，幂等重复拦截 exchange: {} message: {}", exchange, message);
                return;
            }
            throw e;
        } catch (Exception e) {
            log.error("监听积分账户调整成功消息失败 exchange: {} message: {}", exchange, message, e);
            throw e;
        }
    }
}