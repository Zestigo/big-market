package com.c.infrastructure.dao;

import com.c.infrastructure.po.UserAwardRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户中奖记录表数据访问层 (DAO)
 * <p>
 * 职责描述：
 * 1. 持久化存储用户抽奖命中的原始结果。
 * 2. 作为奖品发放的“事实来源”，支持后续异步发奖、定时补偿及人工审计。
 * <p>
 * 核心规范：
 * - 分片规则：本表按 user_id 进行物理分片，调用时必须确保 user_id 字段非空。
 * - 字段约束：插入前需严格校验 award_title、user_id 等数据库非空字段。
 * - 幂等性建议：建议在数据库层增加 business_id 唯一索引，配合 insert 操作防止重复入库。
 *
 * @author cyh
 * @since 2026/02/03
 */
@Mapper
public interface IUserAwardRecordDao {

    /**
     * 持久化单条中奖流水
     * 在抽奖计算完成后即刻调用，锁定用户中奖状态，防止结果丢失。
     *
     * @param userAwardRecord 用户中奖记录实体。
     */
    void insert(UserAwardRecord userAwardRecord);

    /**
     * 更新发奖记录状态为完成态
     * 只有当前状态为 'wait' (等待发奖) 时才允许更新成功
     *
     * @param userAwardRecord 包含 userId, orderId 和目标状态
     * @return 更新影响行数 (1: 成功; 0: 幂等拦截或记录不存在)
     */
    int updateAwardRecordCompletedState(UserAwardRecord userAwardRecord);
}