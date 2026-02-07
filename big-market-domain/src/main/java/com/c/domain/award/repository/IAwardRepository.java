package com.c.domain.award.repository;

import com.c.domain.award.model.aggregate.UserAwardRecordAggregate;

/**
 * 中奖记录仓储接口
 * 1. 领域驱动仓储：定义领域层对中奖数据持久化的标准，屏蔽底层物理存储（MySQL/NoSQL）的具体实现细节。
 * 2. 聚合原子性保障：规定了以聚合根（Aggregate）为单位的写入契约，确保中奖凭证与补偿任务在同一个事务中生效。
 * 3. 跨库分片契约：隐含了基于用户分片键（userId）的存储一致性要求。
 *
 * @author cyh
 * @date 2026/02/01
 */
public interface IAwardRepository {

    /**
     * 保存用户中奖记录聚合对象
     * 1. 原子性写入：实现类必须保证【中奖记录实体】与【消息任务实体】在一个事务内落库。
     * 2. 幂等控制：需处理可能出现的唯一索引冲突（如重复的订单号、奖品发放记录），保障中奖凭证的唯一性。
     * 3. 消息触达保障：该方法执行成功后，需触发（或通过 Job 触发）对应的领域事件外发。
     *
     * @param userAwardRecordAggregate 中奖记录聚合根，封装了中奖凭证与异步发送任务
     */
    void saveUserAwardRecord(UserAwardRecordAggregate userAwardRecordAggregate);

}