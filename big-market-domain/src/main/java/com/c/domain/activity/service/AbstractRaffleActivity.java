package com.c.domain.activity.service;

import com.alibaba.fastjson.JSON;
import com.c.domain.activity.model.entity.*;
import com.c.domain.activity.repositor.IActivityRepository;
import lombok.extern.slf4j.Slf4j;

/**
 * @author cyh
 * @description 抽奖活动抽象类
 * 封装了创建抽奖订单的共用标准流程（模板方法模式），具体的业务实现由子类按需扩展。
 * @date 2026/01/27
 */
@Slf4j
public abstract class AbstractRaffleActivity implements IRaffleOrder {

    /**
     * 活动领域仓储接口，用于数据的持久化与查询
     */
    protected IActivityRepository activityRepository;

    /**
     * 构造函数注入仓储实例
     *
     * @param activityRepository 活动仓储
     */
    public AbstractRaffleActivity(IActivityRepository activityRepository) {
        this.activityRepository = activityRepository;
    }

    /**
     * 执行创建抽奖活动订单的通用流程
     *
     * @param activityShopCartEntity 活动购物车实体，包含关键的 SKU 信息
     * @return ActivityOrderEntity 生成的活动订单实体
     */
    @Override
    public ActivityOrderEntity createRaffleActivityOrder(ActivityShopCartEntity activityShopCartEntity) {
        // 1. 根据传入的 SKU 信息查询活动库存单元（ActivitySkuEntity）
        // SKU 关联了具体的活动 ID 以及该 SKU 对应的次数配置 ID
        ActivitySkuEntity activitySkuEntity =
                activityRepository.queryActivitySku(activityShopCartEntity.getSku());

        // 2. 根据 SKU 中记录的 activityId 查询活动基础信息（ActivityEntity）
        // 包含活动名称、开始/结束时间、活动状态（开启/关闭）等信息
        ActivityEntity activityEntity =
                activityRepository.queryRaffleActivityByActivityId(activitySkuEntity.getActivityId());

        // 3. 根据 SKU 中记录的 activityCountId 查询活动的次数配置信息（ActivityCountEntity）
        // 定义了该活动下，用户总参与次数限制、日参与次数限制、月参与次数限制
        ActivityCountEntity activityCountEntity =
                activityRepository.queryRaffleActivityCountByActivityCountId(activitySkuEntity.getActivityCountId());

        // 4. 日志记录：详细打印查询到的各个领域实体信息，便于线上排查与追溯
        log.info("查询抽奖活动配置结果：SKU信息={}, 活动信息={}, 次数配置={}", JSON.toJSONString(activitySkuEntity),
                JSON.toJSONString(activityEntity), JSON.toJSONString(activityCountEntity));

        // 5. 构建并返回活动订单实体（注：此处后续应补充具体的订单落库逻辑与状态流转）
        return ActivityOrderEntity.builder().build();
    }

}