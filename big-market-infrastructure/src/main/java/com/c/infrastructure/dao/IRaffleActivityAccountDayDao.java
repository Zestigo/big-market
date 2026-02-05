package com.c.infrastructure.dao;

import com.c.infrastructure.po.RaffleActivityAccountDay;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户活动账户（日维度）数据访问接口
 * 1. 瞬时风控：负责记录和校验用户在自然日周期内（24小时）的参与频次。
 * 2. 最小粒度：作为多级额度校验（总-月-日）的最底层，用于应对羊毛党高频刷单及瞬时流量激增。
 *
 * @author cyh
 * @date 2026/02/01
 */
@Mapper
public interface IRaffleActivityAccountDayDao {

    /**
     * 查询用户在特定活动、特定日期的账户额度记录
     *
     * @param raffleActivityAccountDay 包含 userId, activityId 以及 day(格式:yyyy-mm-dd) 的请求对象
     * @return 日度账户持久化对象（包含当日剩余可参与次数）
     */
    RaffleActivityAccountDay queryActivityAccountDayByUserId(RaffleActivityAccountDay raffleActivityAccountDay);

    /**
     * 原子扣减日度账户剩余额度
     * 1. 乐观锁防超卖：SQL 实现必须包含 `day_count_surplus > 0` 的条件，利用数据库行锁保障高并发下的数据一致性。
     * 2. 状态反馈：通过返回受影响行数，告知上层服务该用户当日额度是否已真正扣减成功。
     *
     * @param raffleActivityAccountDay 包含账户标识信息（userId, activityId, day）的对象
     * @return 更新受影响行数。1-扣减成功；0-额度不足或账户异常。
     */
    int updateActivityAccountDaySubtractionQuota(RaffleActivityAccountDay raffleActivityAccountDay);

    /**
     * 新增日度账户记录
     * 当用户当日首次参与活动时，由参与聚合根（Aggregate）触发初始化逻辑。
     * 数据库层面需对 (user_id, activity_id, day) 设置唯一索引，配合上层 try-catch 拦截 DuplicateKeyException，
     * 从而优雅处理极高并发下多个请求同时尝试初始化“今日账户”的情况。
     *
     * @param raffleActivityAccountDay 日度账户初始化信息实体
     */
    void insert(RaffleActivityAccountDay raffleActivityAccountDay);
}