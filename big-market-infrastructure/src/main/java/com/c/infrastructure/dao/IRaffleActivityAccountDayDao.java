package com.c.infrastructure.dao;

import com.c.infrastructure.po.RaffleActivityAccountDay;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户活动账户（日维度）数据访问接口
 * 职责：
 * 1. 额度控制：管理用户每日参与次数的原子性扣减。
 * 2. 状态存储：维护日维度的聚合根快照。
 */
@Mapper
public interface IRaffleActivityAccountDayDao {

    /**
     * 获取日账户快照
     * 场景：用于充血实体初始化时装载数据，或在校验逻辑中查询余量。
     */
    RaffleActivityAccountDay queryActivityAccountDay(RaffleActivityAccountDay raffleActivityAccountDay);

    /**
     * 原子扣减日额度（核心风控手段）
     * 逻辑：update ... set surplus = surplus - 1 where ... and surplus > 0
     *
     * @return 1-扣减成功；0-额度不足（并发冲突或初始额度耗尽）
     */
    int updateActivityAccountDaySubtractionQuota(RaffleActivityAccountDay raffleActivityAccountDay);

    /**
     * 累加/初始化日额度（幂等写）
     * 逻辑：使用 INSERT INTO ... ON DUPLICATE KEY UPDATE 实现
     * 场景：活动返利、手动补单、或账户额度跨日自动初始化。
     *
     * @return 影响行数（1-新插入，2-累加更新）
     */
    int upsertAddAccountQuota(RaffleActivityAccountDay raffleActivityAccountDay);

    /**
     * 查询用户今日活动账户额度
     * 根据用户ID、活动ID及日期（day），获取用户当日的抽奖次数及剩余额度。
     *
     * @param raffleActivityAccountDay 查询请求对象（需包含 userId, activityId, day）
     * @return RaffleActivityAccountDay 用户日账户额度记录
     */
    RaffleActivityAccountDay queryActivityAccountDayByUserId(RaffleActivityAccountDay raffleActivityAccountDay);

}