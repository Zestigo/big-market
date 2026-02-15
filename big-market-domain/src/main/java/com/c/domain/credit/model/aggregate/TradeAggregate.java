package com.c.domain.credit.model.aggregate;

import com.c.domain.credit.event.CreditAdjustSuccessMessageEvent;
import com.c.domain.credit.model.entity.CreditAccountEntity;
import com.c.domain.credit.model.entity.CreditOrderEntity;
import com.c.domain.credit.model.entity.TaskEntity;
import com.c.domain.credit.model.entity.TradeEntity;
import com.c.domain.credit.model.vo.TaskStateVO;
import com.c.types.event.BaseEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.RandomStringUtils;

/**
 * 积分交易聚合根
 *
 * @author cyh
 * @date 2026/02/09
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TradeAggregate {

    /* 用户ID */
    private String userId;

    /* 积分账户实体 */
    private CreditAccountEntity creditAccountEntity;

    /* 积分订单实体 */
    private CreditOrderEntity creditOrderEntity;

    /* 任务实体 */
    private TaskEntity taskEntity;

    /**
     * 静态工厂：构建初始聚合根
     * 职责：封装订单 ID 生成逻辑，初始化核心业务领域对象
     */
    public static TradeAggregate createTradeAggregate(TradeEntity tradeEntity) {
        // 1. 构建流水订单实体：锁定业务单号生成规则
        CreditOrderEntity creditOrderEntity = CreditOrderEntity
                .builder()
                .userId(tradeEntity.getUserId())
                .orderId(RandomStringUtils.randomNumeric(12))
                .tradeName(tradeEntity.getTradeName())
                .tradeType(tradeEntity.getTradeType())
                .tradeAmount(tradeEntity.getTradeAmount())
                .outBusinessNo(tradeEntity.getOutBusinessNo())
                .build();

        // 2. 构建账户变动实体：承载金额调整逻辑
        CreditAccountEntity creditAccountEntity = CreditAccountEntity
                .builder()
                .userId(tradeEntity.getUserId())
                .adjustAmount(tradeEntity.getTradeAmount())
                .tradeType(tradeEntity.getTradeType())
                .build();

        // 3. 返回聚合根实例：确保基础模型完整性
        return TradeAggregate
                .builder()
                .userId(tradeEntity.getUserId())
                .creditOrderEntity(creditOrderEntity)
                .creditAccountEntity(creditAccountEntity)
                .build();
    }

    /**
     * 领域行为：装配消息任务
     * 职责：基于当前聚合根状态闭环创建消息负载，保证订单 ID 全链路一致
     *
     * @param event 信用调整成功事件定义
     */
    public void createMessageTask(CreditAdjustSuccessMessageEvent event) {
        // 1. 构建消息负荷：映射聚合根属性至事件模型
        CreditAdjustSuccessMessageEvent.CreditAdjustSuccessMessage message =
                CreditAdjustSuccessMessageEvent.CreditAdjustSuccessMessage
                        .builder()
                        .userId(this.userId)
                        .orderId(this.creditOrderEntity.getOrderId())
                        .amount(this.creditOrderEntity.getTradeAmount())
                        .outBusinessNo(this.creditOrderEntity.getOutBusinessNo())
                        .build();

        // 2. 包装事件消息：生成消息唯一标识及时间戳
        BaseEvent.EventMessage<CreditAdjustSuccessMessageEvent.CreditAdjustSuccessMessage> messagePayload =
                event.buildEventMessage(message);

        // 3. 内部装配任务：将零件整合进任务实体
        this.taskEntity = createTaskEntity(this.userId, event, messagePayload);
    }

    /**
     * 私有工厂：生产任务零件
     * 约束：强制通过聚合根行为触发，禁止外部越权创建
     */
    private static TaskEntity createTaskEntity(String userId, CreditAdjustSuccessMessageEvent event,
                                               BaseEvent.EventMessage<CreditAdjustSuccessMessageEvent.CreditAdjustSuccessMessage> message) {
        return TaskEntity
                .builder()
                .userId(userId)
                .exchange(event.exchange())
                .routingKey(event.routingKey())
                .messageId(message.getId())
                .message(message)
                .state(TaskStateVO.CREATE)
                .build();
    }
}