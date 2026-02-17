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
        try {
            // 1. 基础参数校验
            String userId = skuRechargeEntity.getUserId();
            Long sku = skuRechargeEntity.getSku();
            String outBusinessNo = skuRechargeEntity.getOutBusinessNo();
            log.info("创建活动订单开始 userId:{} sku:{} outBusinessNo:{}", userId, sku, outBusinessNo);

            if (sku == null || StringUtils.isBlank(userId) || StringUtils.isBlank(outBusinessNo)) {
                log.warn("创建活动订单参数校验失败 userId:{} sku:{} outBusinessNo:{}", userId, sku, outBusinessNo);
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER);
            }

            // 2. 查询是否存在一个月以内的未支付订单
            UnpaidActivityOrderEntity unpaidCreditOrder =
                    activityRepository.queryUnpaidActivityOrder(skuRechargeEntity);
            if (unpaidCreditOrder != null) {
                log.info("创建活动订单拦截，存在一个月内未支付订单 userId:{} sku:{} orderId:{}", userId, sku,
                        unpaidCreditOrder.getOrderId());
                return unpaidCreditOrder;
            }

            // 3. 业务数据聚合查询
            ActivitySkuEntity activitySkuEntity = queryActivitySku(sku);
            ActivityEntity activityEntity = queryRaffleActivityByActivityId(activitySkuEntity.getActivityId());
            ActivityCountEntity activityCountEntity =
                    queryRaffleActivityCountByActivityCountId(activitySkuEntity.getActivityCountId());

            // 4. 执行合规性校验责任链
            IActionChain actionChain = defaultActivityChainFactory.openActionChain();
            actionChain.action(activitySkuEntity, activityEntity, activityCountEntity);

            // 5. 构建订单聚合对象
            CreateQuotaOrderAggregate createQuotaOrderAggregate = buildOrderAggregate(skuRechargeEntity,
                    activitySkuEntity, activityEntity, activityCountEntity);

            // 6. 执行交易策略
            String tradeTypeCode = skuRechargeEntity
                    .getOrderTradeType()
                    .getCode();
            ITradePolicy tradePolicy = tradePolicyGroup.get(tradeTypeCode);
            tradePolicy.trade(createQuotaOrderAggregate);

            // 7. 组装结果
            ActivityOrderEntity activityOrderEntity = createQuotaOrderAggregate.getActivityOrderEntity();
            log.info("创建活动订单成功 userId:{} orderId:{} outBusinessNo:{}", userId, activityOrderEntity.getOrderId(),
                    activityOrderEntity.getOutBusinessNo());

            return UnpaidActivityOrderEntity
                    .builder()
                    .userId(userId)
                    .orderId(activityOrderEntity.getOrderId())
                    .outBusinessNo(activityOrderEntity.getOutBusinessNo())
                    .payAmount(activityOrderEntity.getPayAmount())
                    .build();

        } catch (AppException e) {
            log.error("创建活动订单业务异常 userId:{} code:{} info:{}", skuRechargeEntity.getUserId(), e.getCode(), e.getInfo());
            throw e;
        } catch (Exception e) {
            log.error("创建活动订单系统未知异常 userId:{}", skuRechargeEntity.getUserId(), e);
            throw new AppException(ResponseCode.UN_ERROR, e);
        }
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