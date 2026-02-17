package com.c.infrastructure.dao;

import com.c.infrastructure.po.UserCreditAccount;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户积分账户数据访问接口
 *
 * @author cyh
 * @date 2026/02/16
 */
@Mapper
public interface IUserCreditAccountDao {

    /**
     * 正向增量更新或初始化账户额度 (Upsert)
     *
     * @param userCreditAccount 包含用户ID及待增加的额度信息
     * @return 影响行数
     */
    int upsertAddAccountQuota(UserCreditAccount userCreditAccount);

    /**
     * 逆向原子扣减账户额度
     *
     * @param userCreditAccount 包含用户ID及待扣减的额度信息
     * @return 影响行数（1-成功，0-失败或余额不足）
     */
    int updateSubtractionAmount(UserCreditAccount userCreditAccount);

    /**
     * 查询用户账户是否存在
     *
     * @param userCreditAccount 包含用户ID的查询对象
     * @return 匹配记录数（1-已开户，0-未开户）
     */
    int queryUserCreditAccountCount(UserCreditAccount userCreditAccount);

    /**
     * 查询用户积分账户详情
     *
     * @param account 包含用户ID的查询对象
     * @return 用户积分账户持久化对象
     */
    UserCreditAccount queryUserCreditAccount(UserCreditAccount account);

}