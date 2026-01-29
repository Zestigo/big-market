package com.c.infrastructure.dao;

import com.c.infrastructure.po.RaffleActivityAccount;
import org.apache.ibatis.annotations.Mapper;

/**
 * 抽奖活动账户持久化访问接口
 * <p>
 * 职责：
 * 负责管理用户在特定活动下的参与额度（总额度、日额度、月额度）。
 * 提供账户创建及基于数据库行锁的额度增量更新功能。
 *
 * @author cyh
 * @date 2026/01/29
 */
@Mapper
public interface IRaffleActivityAccountDao {

    /**
     * 初始化用户活动账户
     * 当用户首次参与该活动时，通过此方法创建账户记录。
     * 通常会配合数据库唯一索引（userId + activityId）防止重复创建。
     *
     * @param raffleActivityAccount 账户持久化对象
     */
    void insert(RaffleActivityAccount raffleActivityAccount);

    /**
     * 原子性更新账户额度
     * 该方法通常执行 SQL：update table set total_count_surplus = total_count_surplus - 1 ...
     * 必须在 SQL 层面通过库存检查（如：where total_count_surplus > 0）确保额度不会扣减至负数。
     *
     * @param raffleActivityAccount 包含更新条件（userId, activityId）及变更额度的实体
     * @return 更新受影响的行数。若返回 0，通常表示余额不足或账户不存在。
     */
    int updateAccountQuota(RaffleActivityAccount raffleActivityAccount);

}