package com.c.infrastructure.adapter.repository;

import com.c.domain.activity.model.entity.ActivityCountEntity;
import com.c.domain.activity.model.entity.ActivityEntity;
import com.c.domain.activity.model.entity.ActivitySkuEntity;
import com.c.domain.activity.model.vo.ActivityStateVO;
import com.c.domain.activity.repositor.IActivityRepository;
import com.c.infrastructure.dao.IRaffleActivityCountDao;
import com.c.infrastructure.dao.IRaffleActivityDao;
import com.c.infrastructure.dao.IRaffleActivitySKUDao;
import com.c.infrastructure.po.RaffleActivity;
import com.c.infrastructure.po.RaffleActivityCount;
import com.c.infrastructure.po.RaffleActivitySKU;
import com.c.infrastructure.redis.IRedisService;
import com.c.types.common.Constants;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;

@Repository
public class ActivityRepository implements IActivityRepository {
    @Resource
    private IRedisService redisService;
    @Resource
    private IRaffleActivitySKUDao raffleActivitySKUDao;
    @Resource
    private IRaffleActivityDao raffleActivityDao;
    @Resource
    private IRaffleActivityCountDao raffleActivityCountDao;

    @Override
    public ActivitySkuEntity queryActivitySku(Long sku) {
        RaffleActivitySKU raffleActivitySKU = raffleActivitySKUDao.queryActivitySku(sku);
        return ActivitySkuEntity.builder().sku(raffleActivitySKU.getSku())
                                .activityId(raffleActivitySKU.getActivityId())
                                .activityCountId(raffleActivitySKU.getActivityCountId())
                                .stockCount(raffleActivitySKU.getStockCount())
                                .stockCountSurplus(raffleActivitySKU.getStockCountSurplus()).build();
    }

    @Override
    public ActivityEntity queryRaffleActivityByActivityId(Long activityId) {
        String cacheKey = Constants.RedisKey.ACTIVITY_KEY + activityId;
        ActivityEntity activityEntity = redisService.getValue(cacheKey);
        if (activityEntity != null) return activityEntity;
        RaffleActivity raffleActivity = raffleActivityDao.queryRaffleActivityByActivityId(activityId);
        activityEntity = ActivityEntity.builder().activityId(raffleActivity.getActivityId())
                                       .activityName(raffleActivity.getActivityName())
                                       .activityDesc(raffleActivity.getActivityDesc())
                                       .beginDateTime(raffleActivity.getBeginDateTime())
                                       .endDateTime(raffleActivity.getEndDateTime())
                                       .strategyId(raffleActivity.getStrategyId())
                                       .state(ActivityStateVO.valueOf(raffleActivity.getState())).build();
        redisService.setValue(cacheKey, activityEntity);
        return activityEntity;
    }

    @Override
    public ActivityCountEntity queryRaffleActivityCountByActivityCountId(Long activityCountId) {
        String cacheKey = Constants.RedisKey.ACTIVITY_COUNT_KEY + activityCountId;
        ActivityCountEntity activityCountEntity = redisService.getValue(cacheKey);
        if (activityCountEntity != null) return activityCountEntity;


        RaffleActivityCount raffleActivityCount =
                raffleActivityCountDao.queryRaffleActivityCountByActivityCountId(activityCountId);
        activityCountEntity = ActivityCountEntity.builder()
                                                 .activityCountId(raffleActivityCount.getActivityCountId())
                                                 .totalCount(raffleActivityCount.getTotalCount())
                                                 .dayCount(raffleActivityCount.getDayCount())
                                                 .monthCount(raffleActivityCount.getMonthCount())
                                                 .build();
        redisService.setValue(cacheKey, activityCountEntity);

        return activityCountEntity;
    }
}
