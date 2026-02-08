package com.c.domain.award.service;

import com.c.domain.award.model.entity.DistributeAwardEntity;
import com.c.domain.award.model.entity.UserAwardRecordEntity;

/**
 * 奖品领域服务接口
 * 1. 负责中奖信息的持久化，并同步创建本地消息任务（Task）。
 * 2. 路由并驱动不同类型的奖品发放动作。
 *
 * @author cyh
 * @since 2026/02/01
 */
public interface IAwardService {

    /**
     * 保存用户中奖记录及消息任务
     * 1. 根据中奖单据，将流水记录与异步补偿任务（Task）在事务内同步落地。
     * 2. 记录生成后，触发初始的消息投递流程。
     *
     * @param userAwardRecordEntity 中奖记录实体
     */
    void saveUserAwardRecord(UserAwardRecordEntity userAwardRecordEntity);

    /**
     * 执行奖品发放逻辑
     * 1. 识别奖品类型（积分、实物、优惠券等）并执行对应的发放动作。
     * 2. 发放完成后核销记录状态，确保业务链路闭环。
     *
     * @param distributeAwardEntity 奖品分发实体
     */
    void distributeAward(DistributeAwardEntity distributeAwardEntity);

}