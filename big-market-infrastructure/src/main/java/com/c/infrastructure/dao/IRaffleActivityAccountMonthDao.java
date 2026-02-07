package com.c.infrastructure.dao;

import com.c.infrastructure.po.RaffleActivityAccountMonth;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户活动账户（月维度）数据访问接口
 * 职责：
 * 1. 周期控制：管理用户在自然月内的参与额度，是总额度下的二级漏斗。
 * 2. 幂等更新：支持月度账户的自动初始化与额度追加。
 */
@Mapper
public interface IRaffleActivityAccountMonthDao {

    /**
     * 查询月度账户快照
     * 场景：用于初始化充血实体，校验月度水位。
     * * @param raffleActivityAccountMonth 必须包含 userId, activityId, month
     * @return 月度账户持久化对象
     */
    RaffleActivityAccountMonth queryActivityAccountMonthByUserId(RaffleActivityAccountMonth raffleActivityAccountMonth);

    /**
     * 原子扣减月度剩余额度
     * 核心逻辑：利用数据库行锁与 surplus > 0 条件实现并发安全扣减。
     * * @return 1-成功；0-额度不足
     */
    int updateActivityAccountMonthSubtractionQuota(RaffleActivityAccountMonth raffleActivityAccountMonth);

    /**
     * 幂等累加/初始化月度额度
     * 逻辑：通过 INSERT ... ON DUPLICATE KEY UPDATE 实现。
     * 场景：用户获得月度补偿额度，或月度账户首次触发自动创建。
     * * @return 影响行数 (1-新创建, 2-累加更新)
     */
    int upsertAddAccountQuota(RaffleActivityAccountMonth raffleActivityAccountMonth);

}