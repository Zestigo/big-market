package com.c.domain.activity.service.rule.impl;

import com.c.domain.activity.model.entity.ActivityCountEntity;
import com.c.domain.activity.model.entity.ActivityEntity;
import com.c.domain.activity.model.entity.ActivitySkuEntity;
import com.c.domain.activity.model.vo.ActivitySkuStockKeyVO;
import com.c.domain.activity.repositor.IActivityRepository;
import com.c.domain.activity.service.rule.AbstractActionChain;
import com.c.domain.activity.service.rule.armory.IActivityDispatch;
import com.c.types.enums.ResponseCode;
import com.c.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;


/**
 * 活动规则处理节点：商品库存校验与扣减
 * 职责：
 * 1. 执行 Redis 预扣减库存，保证高并发下的库存不超卖。
 * 2. 扣减成功后，发送延迟消息，异步持久化库存到数据库。
 * 3. 维护责任链的流转，若库存节点成功则进入下一个规则节点。
 *
 * @author cyh
 * @date 2026/01/29
 */
@Slf4j
@Component("activity_sku_stock_action")
public class ActivitySKUStockActionChain extends AbstractActionChain {

    @Resource
    private IActivityDispatch activityDispatch;
    @Resource
    private IActivityRepository activityRepository;

    /**
     * 执行库存扣减逻辑
     *
     * @return true - 库存扣减成功且后续规则执行完毕；false - 流程拦截或校验失败
     */
    @Override
    public boolean action(ActivitySkuEntity activitySkuEntity, ActivityEntity activityEntity,
                          ActivityCountEntity activityCountEntity) {
        log.info("活动责任链-商品库存处理【有效期、状态、库存(sku)】开始。sku:{} activityId:{}", activitySkuEntity.getSku(),
                activityEntity.getActivityId());

        // 1. 预扣减库存：通过 activityDispatch 执行 Redis 原子性扣减（不再内部发消息）
        boolean status = activityDispatch.subtractionActivitySkuStock(activitySkuEntity.getSku(),
                activityEntity.getEndDateTime());

        if (status) {
            log.info("活动责任链-商品库存处理成功。sku:{} activityId:{}", activitySkuEntity.getSku(),
                    activityEntity.getActivityId());

            // 2. 异步同步：【唯一入队处】写入延迟队列，由 Job 批量更新 DB
            activityRepository.activitySkuStockConsumeSendQueue(ActivitySkuStockKeyVO.builder()
                                                                                     .sku(activitySkuEntity.getSku())
                                                                                     .activityId(activityEntity.getActivityId())
                                                                                     .build());

            // 3. 链路流转：当前节点成功，执行责任链下一个节点
            return next() == null || next().action(activitySkuEntity, activityEntity, activityCountEntity);
        }

        // 4. 库存不足或扣减失败：抛出异常
        log.warn("活动责任链-商品库存不足。sku:{} activityId:{}", activitySkuEntity.getSku(),
                activityEntity.getActivityId());
        throw new AppException(ResponseCode.ACTIVITY_SKU_STOCK_ERROR.getCode(),
                ResponseCode.ACTIVITY_SKU_STOCK_ERROR.getInfo());
    }

}