package com.c.domain.activity.service.rule;

import com.c.domain.activity.model.entity.ActivityCountEntity;
import com.c.domain.activity.model.entity.ActivityEntity;
import com.c.domain.activity.model.entity.ActivitySkuEntity;

/**
 * 抽奖活动规则过滤动作链接口
 * 该接口定义了活动校验链路中的标准动作行为，采用责任链模式处理活动准入规则。
 * 继承自 {@link IActionChainArmory} 以具备链路装配与指向能力。
 *
 * @author cyh
 * @date 2026/01/29
 */
public interface IActionChain extends IActionChainArmory {

    /**
     * 执行规则过滤逻辑
     *
     * @param activitySkuEntity   活动SKU实体，包含商品层级的配置信息
     * @param activityEntity      活动基础信息实体，包含活动时间、状态等核心元数据
     * @param activityCountEntity 活动库存/次数实体，包含可参与次数及消耗情况
     * @return 过滤结果：true - 校验通过，允许继续执行后续逻辑或下单；false - 校验拦截，不满足参与条件
     */
    boolean action(ActivitySkuEntity activitySkuEntity, ActivityEntity activityEntity,
                   ActivityCountEntity activityCountEntity);

}