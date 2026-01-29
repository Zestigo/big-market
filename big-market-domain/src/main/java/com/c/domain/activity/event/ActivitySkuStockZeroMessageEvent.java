package com.c.domain.activity.event;

import com.c.types.event.BaseEvent;
import org.apache.commons.lang.RandomStringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;


/**
 * @author cyh
 * @description 活动SKU库存清零消息事件
 * 场景：当 Redis 预扣库存达到 0 时触发，发送 MQ 消息通知数据库同步或执行下架逻辑。
 * @date 2026/01/28
 */
@Component
public class ActivitySkuStockZeroMessageEvent extends BaseEvent<Long> {

    /**
     * 从 application.yml 获取消息队列主题（Topic）。
     * 采用配置注入而非硬编码，方便在开发、测试、生产环境之间灵活切换。
     */
    @Value("${spring.rabbitmq.topic.activity_sku_stock_zero}")
    private String topic;

    /**
     * 构建事件消息体
     *
     * @param sku 触发清零事件的商品 SKU 编号
     * @return 包含唯一标识和时间戳的标准事件消息
     */
    @Override
    public BaseEvent.EventMessage<Long> buildEventMessage(Long sku) {
        return BaseEvent.EventMessage.<Long>builder()
                                     // 生成 11 位随机数字序列作为消息 ID，用于消费者端的幂等性校验或链路追踪
                                     .id(RandomStringUtils.randomNumeric(11))
                                     // 记录消息产生的时间戳
                                     .timestamp(new Date())
                                     // 业务数据：这里传递的是具体的 SKU 编号
                                     .data(sku)
                                     .build();
    }

    /**
     * 返回当前事件绑定的主题名称
     */
    @Override
    public String topic() {
        return topic;
    }
}