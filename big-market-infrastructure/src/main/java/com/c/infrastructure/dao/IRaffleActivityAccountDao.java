package com.c.infrastructure.dao;

import com.c.infrastructure.po.RaffleActivityAccount;
import org.apache.ibatis.annotations.Mapper;

/**
 * 抽奖活动账户（总账）数据访问接口
 * 1. 核心资产管理：负责维护用户在特定活动下的总参与次数、总剩余次数。
 * 2. 镜像同步中心：负责同步并持久化月度、日度账户的额度快照（镜像），确保总表具备全维度查询能力。
 * 3. 并发安全闸口：利用数据库行级锁，确保在高并发环境下，用户额度扣减的原子性与准确性。
 *
 * @author cyh
 * @date 2026/01/29
 */
@Mapper
public interface IRaffleActivityAccountDao {

    /**
     * 初始创建用户活动账户
     * 场景：用户首次在该活动下产生充值或参与行为时触发。
     * 约束：数据库需建立 (user_id, activity_id) 唯一索引，通过物理约束防止账户重复初始化。
     *
     * @param raffleActivityAccount 账户持久化对象
     */
    void insert(RaffleActivityAccount raffleActivityAccount);

    /**
     * 原子性增加账户总额度（充值/赠送）
     * 逻辑：执行 `total_count = total_count + n, total_count_surplus = total_count_surplus + n`。
     *
     * @param raffleActivityAccount 包含 userId, activityId 以及待增加的额度值
     * @return 更新受影响的行数
     */
    int updateAccountQuota(RaffleActivityAccount raffleActivityAccount);

    /**
     * 查询用户活动账户信息
     * 包含：总额度详情、当前月份镜像剩余额度、当前日期镜像剩余额度。
     *
     * @param raffleActivityAccount 包含 userId, activityId 的查询对象
     * @return 活动账户持久化对象
     */
    RaffleActivityAccount queryActivityAccountByUserId(RaffleActivityAccount raffleActivityAccount);

    /**
     * 原子性扣减账户总剩余额度（参与活动消费）
     * 1. 乐观锁防超卖：SQL 层面需包含 `total_count_surplus > 0` 检查。
     * 2. 事务保障：此操作通常作为 `saveCreatePartakeOrderAggregate` 聚合根事务的第一步。
     *
     * @param raffleActivityAccount 包含 userId, activityId 的定位信息
     * @return 更新结果：1-成功；0-额度不足
     */
    int updateActivityAccountSubtractionQuota(RaffleActivityAccount raffleActivityAccount);

    /**
     * 更新月度镜像剩余额度
     * 场景：当月账户（Month Account）发生初始化或变更时，同步更新总表中的月度额度快照。
     * 目的：优化查询性能，使得通过单条总账 SQL 即可获取用户多维度的参与状态。
     *
     * @param raffleActivityAccount 包含 userId, activityId 以及新的 monthCountSurplus 镜像值
     */
    void updateActivityAccountMonthSurplusImageQuota(RaffleActivityAccount raffleActivityAccount);

    /**
     * 更新日度镜像剩余额度
     * 场景：当日账户（Day Account）发生初始化或变更时，同步更新总表中的日度额度快照。
     *
     * @param raffleActivityAccount 包含 userId, activityId 以及新的 dayCountSurplus 镜像值
     */
    void updateActivityAccountDaySurplusImageQuota(RaffleActivityAccount raffleActivityAccount);

}