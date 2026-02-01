package com.c.domain.activity.service.quota;

import com.c.domain.activity.model.entity.ActivityCountEntity;
import com.c.domain.activity.model.entity.ActivityEntity;
import com.c.domain.activity.model.entity.ActivitySkuEntity;
import com.c.domain.activity.repositor.IActivityRepository;
import com.c.domain.activity.service.quota.rule.factory.DefaultActivityChainFactory;

/**
 * @author cyh
 * @description 抽奖活动支撑类
 * 1. 作为抽奖活动领域的基础支撑组件，负责编排底层仓储（Repository）查询逻辑与规则引擎（Chain）的初始化。
 * 2. 设计意图：通过继承或组合的方式，为活动参与、下单、领取等业务场景提供统一的数据查询和校验能力。
 * @date 2026/01/27
 */
public class RaffleActivityAccountQuotaSupport {

    /**
     * 活动规则责任链工厂
     * 用于在处理活动过程中，根据配置加载不同的校验规则（如：活动状态、有效期、黑名单等）。
     */
    protected DefaultActivityChainFactory defaultActivityChainFactory;

    /**
     * 活动领域仓储接口
     * 负责对接基础设施层，完成活动配置数据的获取。
     */
    protected IActivityRepository activityRepository;

    /**
     * 构造函数
     *
     * @param activityRepository          活动仓储实现
     * @param defaultActivityChainFactory 规则链工厂实现
     */
    public RaffleActivityAccountQuotaSupport(IActivityRepository activityRepository,
                                             DefaultActivityChainFactory defaultActivityChainFactory) {
        this.activityRepository = activityRepository;
        this.defaultActivityChainFactory = defaultActivityChainFactory;
    }

    /**
     * 根据 SKU 编码查询活动库存单元配置
     *
     * @param sku 商品库存单位唯一标识
     * @return ActivitySkuEntity 包含活动 ID 与次数配置 ID 的实体
     */
    public ActivitySkuEntity queryActivitySku(Long sku) {
        return activityRepository.queryActivitySku(sku);
    }

    /**
     * 根据活动 ID 查询活动主体基础信息
     *
     * @param activityId 活动唯一标识
     * @return ActivityEntity 包含活动名称、策略 ID、时间范围及状态的实体
     */
    public ActivityEntity queryRaffleActivityByActivityId(Long activityId) {
        return activityRepository.queryRaffleActivityByActivityId(activityId);
    }

    /**
     * 根据次数配置 ID 查询参与频次限制
     *
     * @param activityCountId 关联次数配置标识
     * @return ActivityCountEntity 包含总次数、日次数、月次数限制的配置实体
     */
    public ActivityCountEntity queryRaffleActivityCountByActivityCountId(Long activityCountId) {
        return activityRepository.queryRaffleActivityCountByActivityCountId(activityCountId);
    }

}