package com.c.infrastructure.dao;

import com.c.infrastructure.po.RaffleActivityAccountMonth;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户活动账户（月维度）数据访问接口
 * 1. 频次控制：负责记录和校验用户在自然月周期内的抽奖参与次数。
 * 2. 额度隔离：作为总账户下的二级分账，支撑“每月限领 X 次”等精细化运营规则。
 *
 * @author cyh
 * @date 2026/02/01
 */
@Mapper
public interface IRaffleActivityAccountMonthDao {

    /**
     * 查询用户在特定活动、特定月份的账户额度
     *
     * @param raffleActivityAccountMonth 包含 userId, activityId 以及 month(格式:yyyy-mm) 的请求对象
     * @return 月度账户持久化对象（包含当前月剩余额度）
     */
    RaffleActivityAccountMonth queryActivityAccountMonthByUserId(RaffleActivityAccountMonth raffleActivityAccountMonth);

    /**
     * 原子扣减月度账户剩余额度
     * 1. 乐观锁：SQL 实现中应包含 `SET month_count_surplus = month_count_surplus - 1 WHERE ... AND
     * month_count_surplus > 0`。
     * 2. 安全性：通过数据库行锁确保并发场景下月度限额不被突破（不超卖）。
     *
     * @param raffleActivityAccountMonth 包含 userId, activityId, month 标识的对象
     * @return 更新受影响行数。返回 1 表示扣减成功，0 表示额度已耗尽。
     */
    int updateActivityAccountMonthSubtractionQuota(RaffleActivityAccountMonth raffleActivityAccountMonth);

    /**
     * 新增月度账户记录
     * 当用户在当前自然月首次参与该活动时，由 `saveCreatePartakeOrderAggregate` 事务触发初始化。
     * 数据库层通过 (user_id, activity_id, month) 建立唯一索引，防止高并发下重复创建月账记录。
     *
     * @param raffleActivityAccountMonth 月度账户初始化信息
     */
    void insert(RaffleActivityAccountMonth raffleActivityAccountMonth);

}