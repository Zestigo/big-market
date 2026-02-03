package com.c.infrastructure.dao;

import com.c.infrastructure.po.UserAwardRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户中奖记录表数据访问层 (DAO)
 * 1. 负责用户抽奖命中结果的持久化存储。
 * 2. 记录奖品发放的初始状态，为后续异步发奖或人工补偿提供依据。
 * 1. 插入数据前必须确保 award_title, user_id 等必填字段已赋值（对应数据库 NOT NULL 约束）。
 * 2. 该表通常涉及分库分表（如按 user_id 分片），调用时务必确保分片键不为空。
 *
 * @author cyh
 * @date 2026/02/03
 */
@Mapper
public interface IUserAwardRecordDao {

    /**
     * 插入单条用户中奖记录
     *
     * @param userAwardRecord 用户中奖记录实体，包含：
     * - userId: 用户ID（分片键）
     * - awardId: 奖品ID
     * - awardTitle: 奖品名称（不可为空）
     * - awardState: 发奖状态
     */
    void insert(UserAwardRecord userAwardRecord);

}