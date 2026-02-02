package com.c.domain.award.service;

import com.c.domain.award.model.entity.UserAwardRecordEntity;

/**
 * 奖品领域服务接口
 * 1. 业务契约定义：定义中奖记录持久化及后续发奖动作的触发标准。
 * 2. 状态一致性保障：通过实现类协调仓储层，确保业务处理结果（中奖）与消息补偿任务（发奖）的原子性同步。
 * 3. 核心链路入口：作为抽奖完成后，处理“奖品归属”逻辑的首要领域接口。
 *
 * @author cyh
 * @date 2026/02/01
 */
public interface IAwardService {

    /**
     * 保存用户中奖记录
     * 1. 幂等校验：接收抽奖单据，并根据业务主键（如订单ID）确保不重复记录中奖信息。
     * 2. 事务持久化：将中奖凭证写入库，并同步构建对应的【消息任务记录】，为异步发奖提供凭证。
     * 3. 消息驱动：触发后续的发奖流程（如发送积分、发放优惠券或实物奖品记录生成）。
     *
     * @param userAwardRecordEntity 中奖记录实体，包含：用户ID、活动信息、奖品信息及关联订单号
     */
    void saveUserAwardRecord(UserAwardRecordEntity userAwardRecordEntity);

}