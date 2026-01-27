package com.c.domain.activity.service;

import com.c.domain.activity.repositor.IActivityRepository;
import org.springframework.stereotype.Service;

/**
 * @author cyh
 * @description 抽奖活动服务实现类
 * 该类通过继承 AbstractRaffleActivity 获得了创建订单的标准模板能力，
 * 并在 Spring 环境中注册为 Service，供 API 或 Trigger 层调用。
 * @date 2026/01/27
 */
@Service
public class RaffleActivityService extends AbstractRaffleActivity {

    /**
     * 调用父类构造方法，注入活动仓储实现
     *
     * @param activityRepository 由 Spring 自动注入的活动仓储实现类
     */
    public RaffleActivityService(IActivityRepository activityRepository) {
        super(activityRepository);
    }

}