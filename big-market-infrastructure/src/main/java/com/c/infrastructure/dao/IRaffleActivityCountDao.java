package com.c.infrastructure.dao;

import com.c.infrastructure.po.RaffleActivityCount;
import org.apache.ibatis.annotations.Mapper;

/**
 * 抽奖活动次数配置数据访问接口
 *
 * @author cyh
 * @date 2026/02/16
 */
@Mapper
public interface IRaffleActivityCountDao {

    /**
     * 根据活动次数配置 ID 查询次数限制详情
     *
     * @param activityCountId 活动次数配置唯一标识
     * @return 活动次数配置持久化对象
     */
    RaffleActivityCount queryRaffleActivityCountByActivityCountId(Long activityCountId);

}