package com.c.infrastructure.dao;

import com.c.infrastructure.po.UserCreditAccount;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户积分账户持久化访问对象 (DAO)
 * 职责：负责 `user_credit_account` 表的数据交互。
 * 核心功能包括账户初始化、积分增量更新（充值/返现）以及积分扣减。
 *
 * @author cyh
 * @date 2026/02/07
 */
@Mapper
public interface IUserCreditAccountDao {

    /**
     * 1. 积分增加 (充值/返现)
     * 逻辑：total_amount 和 available_amount 同时增加
     */
    int updateAddAmount(UserCreditAccount userCreditAccount);

    /**
     * 2. 积分扣减 (消费/兑换)
     * 逻辑：仅扣减 available_amount，且必须满足余额校验
     */
    int updateSubtractionAmount(UserCreditAccount userCreditAccount);

    /**
     * 3. 账户初始化
     */
    void insert(UserCreditAccount userCreditAccount);

}