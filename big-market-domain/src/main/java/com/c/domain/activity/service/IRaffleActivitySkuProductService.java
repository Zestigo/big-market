package com.c.domain.activity.service;

import com.c.domain.activity.model.entity.SkuProductEntity;

import java.util.List;

/**
 * 抽奖活动 SKU 产品服务接口
 *
 * @author cyh
 * @date 2026/02/16
 */
public interface IRaffleActivitySkuProductService {

    /**
     * 根据活动 ID 查询 SKU 产品实体列表
     *
     * @param activityId 活动唯一标识 ID
     * @return 该活动关联的 SKU 产品实体集合
     */
    List<SkuProductEntity> querySkuProductEntityListByActivityId(Long activityId);

}