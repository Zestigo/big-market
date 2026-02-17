package com.c.domain.activity.service.product;

import com.c.domain.activity.model.entity.SkuProductEntity;
import com.c.domain.activity.repository.IActivityRepository;
import com.c.domain.activity.service.IRaffleActivitySkuProductService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * 抽奖活动 SKU 产品服务实现类
 *
 * @author cyh
 * @date 2026/02/16
 */
@Service
public class RaffleActivitySkuProductService implements IRaffleActivitySkuProductService {

    @Resource
    private IActivityRepository activityRepository;

    /**
     * 根据活动 ID 查询 SKU 产品实体列表
     *
     * @param activityId 活动唯一标识 ID
     * @return SKU 产品实体列表
     */
    @Override
    public List<SkuProductEntity> querySkuProductEntityListByActivityId(Long activityId) {
        // 直接调用仓储层查询活动关联的商品列表
        return activityRepository.querySkuProductEntityListByActivityId(activityId);
    }
}