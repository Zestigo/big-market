package com.c.infrastructure.dao;

import com.c.infrastructure.po.RaffleActivityCount;
import org.apache.ibatis.annotations.Mapper;

/**
 * 抽奖活动次数配置数据访问接口
 * 负责查询活动维度的次数限制规则。该配置定义了用户参与该活动时的“初始额度”或“充值额度”标准。
 * 与 {@link IRaffleActivityAccountDao} 不同，本接口操作的是配置模板，而非用户具体的账户余额。
 *
 * @author cyh
 * @date 2026/01/29
 */
@Mapper
public interface IRaffleActivityCountDao {

    /**
     * 根据活动次数配置ID查询次数限制详情
     * 1. 在活动 SKU 加载时，关联查询该 SKU 对应的次数规则。
     * 2. 在创建用户活动账户（Account）时，参考此配置初始化用户的可用额度。
     *
     * @param activityCountId 活动次数配置唯一标识（通常关联自 Activity 或 SKU 表）
     * @return 活动次数配置持久化对象；包含总次数、日次数、月次数等限制
     */
    RaffleActivityCount queryRaffleActivityCountByActivityCountId(Long activityCountId);
}