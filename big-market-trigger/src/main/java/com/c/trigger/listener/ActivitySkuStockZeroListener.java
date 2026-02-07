package com.c.trigger.listener;

import com.c.domain.activity.service.IRaffleActivitySkuStockService;
import com.c.types.event.BaseEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 基础设施层：活动 SKU 库存售罄熔断监听器
 * 1. 实时感知：监听 Redis 缓存层由于预扣减触发的库存售罄（Zero）信号。
 * 2. 状态强更：同步将物理数据库中的库存置零，确保持久化层与缓存层的状态最终一致性。
 * 3. 异步熔断：通过设置售罄标识位，切断延迟任务（UpdateActivitySkuStockJob）对数据库的无效写操作。
 *
 * @author cyh
 * @date 2026/02/05
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActivitySkuStockZeroListener {

    private final IRaffleActivitySkuStockService skuStock;

    /**
     * 消费库存售罄信号消息
     * 1. 消息解包：从领域事件中解析出目标 SKU 标识。
     * 2. 物理清零：执行数据库 UPDATE 强制将库存修正为 0。
     * 3. 标识注入：在本地或分布式缓存中注入售罄标记位（Flag），用于拦截异步 Job 的过期更新。
     *
     * @param eventMessage 领域事件载体，包含售罄的 SKU 编号
     */
    @RabbitListener(queues = "${spring.rabbitmq.topic.activity_sku_stock.queue}")
    public void onMessage(BaseEvent.EventMessage<Long> eventMessage) {
        try {
            // [1] 数据有效性校验
            Long sku = eventMessage.getData();
            if (null == sku) {
                log.warn("【熔断预警】接收到空值售罄消息，已自动拦截 | Topic: activity_sku_stock");
                return;
            }

            log.info("【熔断启动】开始处理 SKU 库存售罄信号 | SKU: {} | 事件标识: {}", sku, eventMessage.getId());

            // [2] 驱动领域服务：强制清空数据库库存
            // 注意：此操作通常配合 UPDATE ... SET stock = 0 使用
            skuStock.zeroOutActivitySkuStock(sku);

            // [3] 设置售罄熔断标识
            // 作用：当 Job 尝试执行合并扣减时，会优先检查此标识，若已售罄则跳过数据库写入
            skuStock.setSkuStockZeroFlag(sku);

            log.info("【熔断成功】SKU 库存物理清零与拦截标识设置完成 | SKU: {}", sku);

        } catch (Exception e) {
            // [4] 异常透传：确保 MQ 能够根据配置进行重试或转入死信队列 (DLX)
            log.error("【熔断失败】消费库存售罄消息发生异常 | SKU: {} | 详情: ", eventMessage.getData(), e);
            throw e;
        }
    }
}