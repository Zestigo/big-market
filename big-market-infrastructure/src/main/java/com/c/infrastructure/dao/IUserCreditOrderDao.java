package com.c.infrastructure.dao;

import com.c.infrastructure.po.UserCreditOrder;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户积分流水订单 DAO
 * 职责：负责积分交易流水的持久化，是系统幂等性的核心物理防线。
 *
 * @author cyh
 * @date 2026/02/08
 */
@Mapper
public interface IUserCreditOrderDao {

    /**
     * 插入积分交易流水
     * 1. 唯一索引：数据库层必须在 out_business_no 上建立唯一索引（Unique Index）。
     * 2. 幂等控制：若触发 DuplicateKeyException，说明该笔交易已处理，由 Repository 捕获并处理。
     *
     * @param userCreditOrder 积分流水持久化对象
     */
    void insert(UserCreditOrder userCreditOrder);

    /**
     * 根据外部业务单号查询流水（可选，用于手动对账或补偿逻辑）
     *
     * @param outBusinessNo 外部业务单号（如抽奖单号）
     * @return 积分流水记录
     */
    UserCreditOrder queryOrderCompleted(String outBusinessNo);

}