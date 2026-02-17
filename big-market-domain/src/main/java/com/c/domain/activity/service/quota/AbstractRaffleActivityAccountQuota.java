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
     * 创建活动订单
     *
     * @param skuRechargeEntity 充值实体对象，包含用户、SKU及外部业务单号
     * @return 未支付订单业务实体
     */
    @Override
    public UnpaidActivityOrderEntity createOrder(SkuRechargeEntity skuRechargeEntity) {
        // 1. 基础参数校验
        String userId = skuRechargeEntity.getUserId();
        Long sku = skuRechargeEntity.getSku();
        String outBusinessNo = skuRechargeEntity.getOutBusinessNo();
        if (sku == null || StringUtils.isBlank(userId) || StringUtils.isBlank(outBusinessNo)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER);
        }

        // 2. 查询是否存在一个月以内的未支付订单，防止重复创建
        UnpaidActivityOrderEntity unpaidCreditOrder = activityRepository.queryUnpaidActivityOrder(skuRechargeEntity);
        if (unpaidCreditOrder != null) return unpaidCreditOrder;

        // 3. 业务数据聚合查询：获取 SKU、活动配置及次数限制
        ActivitySkuEntity activitySkuEntity = queryActivitySku(sku);
        ActivityEntity activityEntity = queryRaffleActivityByActivityId(activitySkuEntity.getActivityId());
        ActivityCountEntity activityCountEntity =
                queryRaffleActivityCountByActivityCountId(activitySkuEntity.getActivityCountId());

        // 4. 执行合规性校验责任链（包含活动状态、库存、风控规则等）
        IActionChain actionChain = defaultActivityChainFactory.openActionChain();
        actionChain.action(activitySkuEntity, activityEntity, activityCountEntity);

        // 5. 构建订单聚合对象
        CreateQuotaOrderAggregate createQuotaOrderAggregate = buildOrderAggregate(skuRechargeEntity,
                activitySkuEntity, activityEntity, activityCountEntity);

        // 6. 执行交易策略（根据订单交易类型：如积分兑换、返利等）
        ITradePolicy tradePolicy = tradePolicyGroup.get(skuRechargeEntity
                .getOrderTradeType()
                .getCode());
        tradePolicy.trade(createQuotaOrderAggregate);

        // 7. 组装并返回未支付订单实体信息
        ActivityOrderEntity activityOrderEntity = createQuotaOrderAggregate.getActivityOrderEntity();
        return UnpaidActivityOrderEntity
                .builder()
                .userId(userId)
                .orderId(activityOrderEntity.getOrderId())
                .outBusinessNo(activityOrderEntity.getOutBusinessNo())
                .payAmount(activityOrderEntity.getPayAmount())
                .build();
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