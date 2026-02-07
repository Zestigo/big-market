package com.c.domain.activity.service.quota;

import com.c.domain.activity.model.aggregate.CreateQuotaOrderAggregate;
import com.c.domain.activity.model.entity.*;
import com.c.domain.activity.repository.IActivityRepository;
import com.c.domain.activity.service.IRaffleActivityAccountQuotaService;
import com.c.domain.activity.service.quota.rule.IActionChain;
import com.c.domain.activity.service.quota.rule.factory.DefaultActivityChainFactory;
import com.c.types.enums.ResponseCode;
import com.c.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * @author cyh
 * @description 抽奖活动抽象类
 * 1. 职责：定义创建抽奖活动订单的标准模板流程，编排基础信息查询、规则校验、聚合构建及持久化操作。
 * 2. 设计模式：模板方法模式。通过 {@link #createOrder} 定义骨架，具体的聚合构建和保存交由子类实现。
 * 3. 支撑类继承：继承自 {@link RaffleActivityAccountQuotaSupport}，获取基础数据的查询能力和规则链工厂支撑。
 * @date 2026/01/27
 */
@Slf4j
public abstract class AbstractRaffleActivityAccountQuota extends RaffleActivityAccountQuotaSupport implements IRaffleActivityAccountQuotaService {

    /**
     * 构造方法，注入领域仓储与规则链工厂
     * * @param activityRepository 活动领域仓储接口
     *
     * @param defaultActivityChainFactory 活动规则责任链工厂
     */
    public AbstractRaffleActivityAccountQuota(IActivityRepository activityRepository,
                                              DefaultActivityChainFactory defaultActivityChainFactory) {
        super(activityRepository, defaultActivityChainFactory);
    }

    /**
     * 执行创建抽奖活动充值订单的模板方法
     * 该方法锁定了业务执行顺序，确保先校验、后查询、再过滤、最后持久化的原则。
     *
     * @param skuRechargeEntity 活动充值意图实体，包含用户ID、SKU、幂等单号
     * @return String 返回生成的活动参与订单 ID
     * @throws AppException 业务异常，如参数错误、规则拦截等
     */
    @Override
    public String createOrder(SkuRechargeEntity skuRechargeEntity) {
        // 1. 参数校验：确保基础业务字段不为空，保障后续查询的安全性
        String userId = skuRechargeEntity.getUserId();
        Long sku = skuRechargeEntity.getSku();
        String outBusinessNo = skuRechargeEntity.getOutBusinessNo();
        if (sku == null || StringUtils.isBlank(userId) || StringUtils.isBlank(outBusinessNo)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER);
        }

        // 2. 数据查询：通过调用 Support 层方法获取当前活动参与所需的完整领域对象上下文
        // 2.1 获取 SKU 配置信息（关联活动 ID 与次数 ID）
        ActivitySkuEntity activitySkuEntity = queryActivitySku(sku);

        // 2.2 获取活动基础配置（状态、有效期、策略 ID）
        ActivityEntity activityEntity = queryRaffleActivityByActivityId(activitySkuEntity.getActivityId());

        // 2.3 获取次数限制配置（总次数、日/月限额）
        ActivityCountEntity activityCountEntity =
                queryRaffleActivityCountByActivityCountId(activitySkuEntity.getActivityCountId());

        // 3. 规则校验：利用责任链模式对当前请求进行业务合规性过滤
        // 包含：活动状态校验、库存校验、风控校验等。如果校验不通过，内部会抛出异常中断流程。
        IActionChain actionChain = defaultActivityChainFactory.openActionChain();
        actionChain.action(activitySkuEntity, activityEntity, activityCountEntity);

        // 4. 构建订单聚合对象：由子类根据具体业务场景实现（如不同类型的活动可能有不同的账户处理方式）
        // 聚合对象确保了订单记录与账户变动在领域模型上的一致性。
        CreateQuotaOrderAggregate createQuotaOrderAggregate = buildOrderAggregate(skuRechargeEntity,
                activitySkuEntity, activityEntity, activityCountEntity);

        // 5. 订单持久化：执行数据库写入操作，将聚合对象中的数据保存至对应表中
        // 该步骤通常伴随着数据库事务，确保订单与账户数据的原子性更新。
        doSaveOrder(createQuotaOrderAggregate);

        // 6. 返回订单流水单号，完成充值/下单流程
        log.info("创建抽奖活动订单成功：userId:{} sku:{} orderId:{}", userId, sku, createQuotaOrderAggregate
                .getActivityOrderEntity().getOrderId());
        return createQuotaOrderAggregate.getActivityOrderEntity().getOrderId();
    }

    /**
     * 构建订单聚合对象（抽象方法，由子类实现）
     * 职责：将查询到的各种实体进行业务组装，封装进 {@link CreateQuotaOrderAggregate} 聚合中。
     */
    protected abstract CreateQuotaOrderAggregate buildOrderAggregate(SkuRechargeEntity skuRechargeEntity,
                                                                     ActivitySkuEntity activitySkuEntity,
                                                                     ActivityEntity activityEntity,
                                                                     ActivityCountEntity activityCountEntity);

    /**
     * 保存订单及相关账户操作（抽象方法，由子类实现）
     * 职责：对接基础设施层，完成数据的落库存储。
     */
    protected abstract void doSaveOrder(CreateQuotaOrderAggregate createQuotaOrderAggregate);
}