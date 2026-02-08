package com.c.domain.award.repository;

import com.c.domain.award.model.aggregate.GiveOutPrizesAggregate;
import com.c.domain.award.model.aggregate.UserAwardRecordAggregate;

/**
 * 奖品发放领域仓储接口
 *
 * @author cyh
 * @since 2026/02/01
 */
public interface IAwardRepository {

    /**
     * 保存用户中奖记录
     * 1. 事务内同步写入中奖流水记录和任务补偿记录。
     * 2. 通过 orderId 保证幂等，防止重复写入。
     *
     * @param userAwardRecordAggregate 中奖记录聚合根
     */
    void saveUserAwardRecord(UserAwardRecordAggregate userAwardRecordAggregate);

    /**
     * 查询奖品配置信息
     *
     * @param awardId 奖品 ID
     * @return 奖品业务配置字符串（通常为 JSON）
     */
    String queryAwardConfig(Integer awardId);

    /**
     * 持久化发奖记录与资产变更
     * 1. 事务内完成积分等账户更新与中奖记录状态核销。
     * 2. 利用状态机保证单次发奖的幂等性。
     *
     * @param giveOutPrizesAggregate 发奖记录聚合根
     */
    void saveGiveOutPrizesAggregate(GiveOutPrizesAggregate giveOutPrizesAggregate);

    /**
     * 查询奖品业务标识 Key
     *
     * @param awardId 奖品 ID
     * @return 奖品业务 Key（用于策略路由）
     */
    String queryAwardKey(Integer awardId);
}