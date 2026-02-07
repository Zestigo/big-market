package com.c.domain.rebate.service;

import com.c.domain.rebate.event.SendRebateMessageEvent;
import com.c.domain.rebate.model.aggregate.BehaviorRebateAggregate;
import com.c.domain.rebate.model.entity.BehaviorEntity;
import com.c.domain.rebate.model.entity.BehaviorRebateOrderEntity;
import com.c.domain.rebate.model.entity.TaskEntity;
import com.c.domain.rebate.model.vo.DailyBehaviorRebateVO;
import com.c.domain.rebate.model.vo.TaskStateVO;
import com.c.domain.rebate.repository.IBehaviorRebateRepository;
import com.c.types.common.Constants;
import com.c.types.event.BaseEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 领域服务实现：行为返利编排
 * 1. 规则检索：根据用户行为（如签到、分享）匹配对应的返利配置。
 * 2. 聚合编排：将返利流水记录与本地消息任务（Task）进行关联绑定。
 * 3. 事务预控：构建聚合根集合，交由仓储层执行“一行为多奖励”的原子化持久化。
 *
 * @author cyh
 * @date 2026/02/05
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BehaviorRebateService implements IBehaviorRebateService {

    /** 返利领域仓储：负责规则查询与数据持久化 */
    private final IBehaviorRebateRepository behaviorRebateRepository;

    /** 返利消息事件构建器：提供 MQ 路由契约与消息包装 */
    private final SendRebateMessageEvent sendRebateMessageEvent;

    /**
     * 创建行为返利订单
     * 1. 匹配规则：根据行为类型获取返利配置（一个行为可能对应积分、抽奖机会等多个返利项）。
     * 2. 循环构建：为每个返利项生成唯一的业务幂等键（bizId）与订单号。
     * 3. 封装任务：构建包含 Exchange 与 RoutingKey 的补偿任务实体，确保消息投递路径准确。
     * 4. 批量保存：通过聚合根模式确保“业务单据+消息任务”的本地事务一致性。
     *
     * @param behaviorEntity 触发返利的用户行为实体
     * @return 成功生成的返利订单号集合
     */
    @Override
    public List<String> createOrder(BehaviorEntity behaviorEntity) {
        // 1. 查询匹配的返利配置
        String userId = behaviorEntity.getUserId();
        String outBusinessNo = behaviorEntity.getOutBusinessNo();
        List<DailyBehaviorRebateVO> rebateConfigs =
                behaviorRebateRepository.queryDailyBehaviorRebateConfig(behaviorEntity.getBehaviorTypeVO());

        if (rebateConfigs == null || rebateConfigs.isEmpty()) {
            log.info("未匹配到返利配置 | userId: {} | behaviorType: {}", userId, behaviorEntity.getBehaviorTypeVO());
            return Collections.emptyList();
        }

        List<String> orderIds = new ArrayList<>();
        List<BehaviorRebateAggregate> rebateAggregates = new ArrayList<>();

        // 2. 遍历配置，构建返利聚合项
        for (DailyBehaviorRebateVO config : rebateConfigs) {
            // 2.1 生成业务唯一标识 (幂等键)：userId_rebateType_outBusinessNo
            String bizId = userId + Constants.UNDERLINE + config.getRebateType() + Constants.UNDERLINE + outBusinessNo;

            // 2.2 构建返利订单实体
            BehaviorRebateOrderEntity rebateOrder = BehaviorRebateOrderEntity
                    .builder()
                    .userId(userId)
                    .orderId(RandomStringUtils.randomNumeric(12))
                    .behaviorType(config.getBehaviorType())
                    .rebateDesc(config.getRebateDesc())
                    .rebateType(config.getRebateType())
                    .rebateConfig(config.getRebateConfig())
                    .outBusinessNo(outBusinessNo)
                    .bizId(bizId)
                    .build();

            orderIds.add(rebateOrder.getOrderId());

            // 2.3 构建消息载体
            SendRebateMessageEvent.RebateMessage msgPayload = SendRebateMessageEvent.RebateMessage
                    .builder()
                    .userId(userId)
                    .rebateDesc(config.getRebateDesc())
                    .rebateType(config.getRebateType())
                    .rebateConfig(config.getRebateConfig())
                    .bizId(bizId)
                    .build();

            // 2.4 包装领域事件
            BaseEvent.EventMessage<SendRebateMessageEvent.RebateMessage> eventWrapper =
                    sendRebateMessageEvent.buildEventMessage(msgPayload);

            // 2.5 构建补偿任务实体（修正：注入 RoutingKey）
            TaskEntity task = TaskEntity
                    .builder()
                    .userId(userId)
                    .exchange(sendRebateMessageEvent.exchange())
                    .routingKey(sendRebateMessageEvent.routingKey())
                    .messageId(eventWrapper.getId())
                    .message(eventWrapper)
                    .state(TaskStateVO.CREATE)
                    .build();

            // 2.6 组装聚合根
            BehaviorRebateAggregate aggregate = BehaviorRebateAggregate
                    .builder()
                    .userId(userId)
                    .behaviorRebateOrderEntity(rebateOrder)
                    .taskEntity(task)
                    .build();

            rebateAggregates.add(aggregate);
        }

        // 3. 执行持久化（仓储层实现本地事务）
        behaviorRebateRepository.saveUserRebateRecord(userId, rebateAggregates);

        return orderIds;
    }

    /**
     * 根据外部业务单号查询用户返利流水记录
     * 主要用于业务幂等校验，防止同一外部单据触发多次返利发放。
     *
     * @param userId        用户唯一ID
     * @param outBusinessNo 外部业务单号（如：签到日期、外部交易流水）
     * @return 行为返利订单流水实体列表
     */
    @Override
    public List<BehaviorRebateOrderEntity> queryOrderByOutBusinessNo(String userId, String outBusinessNo) {
        return behaviorRebateRepository.queryOrderByOutBusinessNo(userId, outBusinessNo);
    }
}