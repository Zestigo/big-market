package com.c.domain.activity.service.quota;

import com.c.domain.activity.model.aggregate.CreateQuotaOrderAggregate;
import com.c.domain.activity.model.entity.*;
import com.c.domain.activity.repository.IActivityRepository;
import com.c.domain.activity.service.IRaffleActivityAccountQuotaService;
import com.c.domain.activity.service.quota.policy.ITradePolicy;
import com.c.domain.activity.service.quota.rule.IActionChain;
import com.c.domain.activity.service.quota.rule.factory.DefaultActivityChainFactory;
import com.c.types.enums.ResponseCode;
import com.c.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;

/**
 * 抽奖活动抽象类
 * 定义创建抽奖活动订单的标准模板流程，编排基础信息查询、规则校验、聚合构建及持久化操作。
 *
 * @author cyh
 * @date 2026/01/27
 */
@Slf4j
public abstract class AbstractRaffleActivityAccountQuota extends RaffleActivityAccountQuotaSupport implements IRaffleActivityAccountQuotaService {

    /** 交易策略映射组 */
    private final Map<String, ITradePolicy> tradePolicyGroup;

    /**
     * 注入领域仓储、规则链工厂与交易策略组
     *
     * @param activityRepository          活动领域仓储接口
     * @param defaultActivityChainFactory 活动规则责任链工厂
     * @param tradePolicyGroup            交易策略映射组
     */
    public AbstractRaffleActivityAccountQuota(IActivityRepository activityRepository,
                                              DefaultActivityChainFactory defaultActivityChainFactory, Map<String,
                    ITradePolicy> tradePolicyGroup) {
        super(activityRepository, defaultActivityChainFactory);
        this.tradePolicyGroup = tradePolicyGroup;
    }

    /**
     * 执行创建抽奖活动充值订单的模板方法
     * 按照参数校验、数据查询、规则过滤、聚合构建、策略执行的顺序锁定业务流水线。
     *
     * @param skuRechargeEntity 活动充值意图实体
     * @return 返回生成的活动参与订单 ID
     * @throws AppException 业务异常
     */
    @Override
    public String createOrder(SkuRechargeEntity skuRechargeEntity) {
        // 1. 参数校验
        String userId = skuRechargeEntity.getUserId();
        Long sku = skuRechargeEntity.getSku();
        String outBusinessNo = skuRechargeEntity.getOutBusinessNo();
        if (sku == null || StringUtils.isBlank(userId) || StringUtils.isBlank(outBusinessNo)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER);
        }

        // 2. 数据查询：获取 SKU、活动配置及次数限制配置
        ActivitySkuEntity activitySkuEntity = queryActivitySku(sku);
        ActivityEntity activityEntity = queryRaffleActivityByActivityId(activitySkuEntity.getActivityId());
        ActivityCountEntity activityCountEntity =
                queryRaffleActivityCountByActivityCountId(activitySkuEntity.getActivityCountId());

        // 3. 规则校验：利用责任链进行合规性过滤（状态、库存、风控等）
        IActionChain actionChain = defaultActivityChainFactory.openActionChain();
        actionChain.action(activitySkuEntity, activityEntity, activityCountEntity);

        // 4. 构建订单聚合对象：由子类实现具体业务场景的组装
        CreateQuotaOrderAggregate createQuotaOrderAggregate = buildOrderAggregate(skuRechargeEntity,
                activitySkuEntity, activityEntity, activityCountEntity);

        // 5. 交易策略执行：根据交易类型（如积分兑换、返利等）执行对应的流程
        ITradePolicy tradePolicy = tradePolicyGroup.get(skuRechargeEntity
                .getOrderTradeType()
                .getCode());
        tradePolicy.trade(createQuotaOrderAggregate);

        // 6. 返回订单单号
        log.info("创建抽奖活动订单成功：userId:{} sku:{} orderId:{}", userId, sku, createQuotaOrderAggregate
                .getActivityOrderEntity()
                .getOrderId());
        return createQuotaOrderAggregate
                .getActivityOrderEntity()
                .getOrderId();
    }

    /**
     * 构建订单聚合对象
     * 由子类将查询到的实体组装进聚合根中，确保业务逻辑一致性。
     */
    protected abstract CreateQuotaOrderAggregate buildOrderAggregate(SkuRechargeEntity skuRechargeEntity,
                                                                     ActivitySkuEntity activitySkuEntity,
                                                                     ActivityEntity activityEntity,
                                                                     ActivityCountEntity activityCountEntity);

}