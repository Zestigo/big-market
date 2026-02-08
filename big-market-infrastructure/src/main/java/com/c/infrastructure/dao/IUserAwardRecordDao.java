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
     * 更新中奖流水为完成状态
     * 采用状态机更新机制（例如：状态从 待处理 变为 已完成），支持重试幂等。
     *
     * @param userAwardRecordReq 必须包含 userId (分片键) 和业务主键标识
     * @return 更新结果：1-成功；0-失败（记录不存在或已被其他事务抢先处理）
     */
    int updateAwardRecordCompletedState(UserAwardRecord userAwardRecordReq);
}