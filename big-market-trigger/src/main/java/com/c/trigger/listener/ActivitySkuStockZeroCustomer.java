package com.c.trigger.listener;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.c.domain.activity.service.ISkuStock;
import com.c.types.event.BaseEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 活动 SKU 库存售罄消息监听器
 * 职责：
 * 1. 响应缓存层发出的库存枯竭信号，执行数据库层面的状态强制同步（置零）。
 * 2. 标记售罄状态，拦截后续 Job 的无效异步更新。
 * 3. 确保持久化层与缓存层在极端并发下的状态最终一致。
 *
 * @author cyh
 * @date 2026/01/31
 */
@Slf4j
@Component
public class ActivitySkuStockZeroCustomer {

    @Resource
    private ISkuStock skuStock;

    /**
     * 消费库存售罄信号消息
     *
     * @param message 消息体，包含售罄的商品 SKU 信息
     */
    @RabbitListener(queues = "${spring.rabbitmq.topic.activity_sku_stock.queue}")
    public void listener(String message) {
        try {
            // 1. 解析消息获取 SKU
            BaseEvent.EventMessage<Long> eventMessage = JSON.parseObject(message, new TypeReference<BaseEvent.EventMessage<Long>>(){}.getType());
            Long sku = eventMessage.getData();

            // 2. 强刷数据库（置零）
            skuStock.zeroOutActivitySkuStock(sku);

            // 3. 【核心步骤】设置 Redis 售罄拦截标识
            skuStock.setSkuStockZeroFlag(sku);

            log.info("【Trigger】实时清零与拦截标识设置成功 sku:{}", sku);
        } catch (Exception e) {
            log.error("【Trigger】消费库存售罄消息异常", e);
            throw e;
        }
    }
}