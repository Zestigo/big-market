package com.c.infrastructure.dao;

import com.c.infrastructure.po.UserCreditAccount;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户积分账户持久化访问对象 (DAO)
 * 职责：定义账户表的原子化物理操作，是账务数据一致性的最终防线。
 *
 * @author cyh
 * @date 2026/02/08
 */
@Mapper
public interface IUserCreditAccountDao {

    /**
     * 正向增量更新 / 账户初始化 (Upsert)
     * 逻辑：如果账户不存在则 insert 并初始化，如果存在则执行原子加法。
     * 场景：中奖入账、充值、活动返现。
     *
     * @param userCreditAccount 包含 userId, totalAmount, availableAmount
     * @return 影响行数 (1-新增, 2-更新)
     */
    int upsertAddAccountQuota(UserCreditAccount userCreditAccount);

    /**
     * 逆向原子扣减
     * 逻辑：同步扣减 total 和 available，SQL 内部通过 WHERE 确保余额不为负。
     * 场景：积分兑换、抽奖消耗、积分核销。
     *
     * @param userCreditAccount 包含 userId, totalAmount, availableAmount
     * @return 影响行数 (1-扣减成功, 0-余额不足或账户不存在)
     */
    int updateSubtractionAmount(UserCreditAccount userCreditAccount);

    /**
     * 查询账户存量状态
     * 逻辑：轻量级 count 查询，用于业务前置判断。
     *
     * @param userCreditAccount 包含 userId (Sharding 路由必备)
     * @return 命中行数 (1-已开户, 0-未开户)
     */
    int queryUserCreditAccountCount(UserCreditAccount userCreditAccount);

}