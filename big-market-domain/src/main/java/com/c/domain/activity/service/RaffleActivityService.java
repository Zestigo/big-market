package com.c.domain.activity.service;

import com.c.domain.activity.model.aggregate.CreateOrderAggregate;
import com.c.domain.activity.model.entity.*;
import com.c.domain.activity.model.vo.OrderStateVO;
import com.c.domain.activity.repositor.IActivityRepository;
import com.c.domain.activity.service.rule.factory.DefaultActivityChainFactory;
import org.apache.commons.lang.RandomStringUtils;
import org.springframework.stereotype.Service;

import java.util.Date;

/**
 * @author cyh
 * @description 抽奖活动服务实现类
 * 该类通过继承 AbstractRaffleActivity 获得了创建订单的标准模板能力，
 * 并在 Spring 环境中注册为 Service，供 API 或 Trigger 层调用。
 * @date 2026/01/27
 */
@Service
public class RaffleActivityService extends AbstractRaffleActivity {

    /**
     * 调用父类构造方法，注入活动仓储实现
     *
     * @param activityRepository 由 Spring 自动注入的活动仓储实现类
     */
    public RaffleActivityService(IActivityRepository activityRepository,
                                 DefaultActivityChainFactory defaultActivityChainFactory) {
        super(activityRepository, defaultActivityChainFactory);
    }

    @Override
    protected CreateOrderAggregate buildOrderAggregate(SkuRechargeEntity skuRechargeEntity,
                                                       ActivitySkuEntity activitySkuEntity,
                                                       ActivityEntity activityEntity,
                                                       ActivityCountEntity activityCountEntity) {
        ActivityOrderEntity activityOrderEntity = ActivityOrderEntity.builder()
                                                                     .sku(skuRechargeEntity.getSku())
                                                                     .userId(skuRechargeEntity.getUserId())
                                                                     .activityId(activityEntity.getActivityId())
                                                                     .activityName(activityEntity.getActivityName())
                                                                     .strategyId(activityEntity.getStrategyId())
                                                                     .outBusinessNo(skuRechargeEntity.getOutBusinessNo())
                                                                     .orderId(RandomStringUtils.randomNumeric(12))
                                                                     // 公司里一般会有专门的雪花算法UUID服务，我们这里直接生成个12 位就可以了。
                                                                     .orderTime(new Date())
                                                                     .totalCount(activityCountEntity.getTotalCount())
                                                                     .dayCount(activityCountEntity.getTotalCount())
                                                                     .monthCount(activityCountEntity.getMonthCount())
                                                                     .state(OrderStateVO.completed).build();

        return CreateOrderAggregate.builder().userId(skuRechargeEntity.getUserId())
                                   .activityId(activitySkuEntity.getActivityId())
                                   .totalCount(activityCountEntity.getTotalCount())
                                   .dayCount(activityCountEntity.getDayCount())
                                   .monthCount(activityCountEntity.getMonthCount())
                                   .activityOrderEntity(activityOrderEntity).build();

    }

    @Override
    protected void doSaveOrder(CreateOrderAggregate createOrderAggregate) {
        activityRepository.doSaveOrder(createOrderAggregate);
    }

}