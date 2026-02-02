package com.c.domain.award.model.aggregate;

import com.c.domain.award.model.entity.TaskEntity;
import com.c.domain.award.model.entity.UserAwardRecordEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户中奖记录聚合根
 * 1. 事务边界定义：封装中奖记录与补偿任务，确保两者在仓储层落库时处于同一个数据库本地事务中。
 * 2. 最终一致性载体：通过将业务数据（AwardRecord）与通信数据（Task）聚合，保障中奖后“发奖消息”必定入库待发。
 * 3. 领域一致性：维护“中奖”这一核心领域事件及其后续副作用（Side Effect）的完整性。
 *
 * @author cyh
 * @date 2026/02/01
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserAwardRecordAggregate {

    /**
     * 用户中奖记录实体
     * 承载核心业务数据，如：用户ID、奖品ID、中奖时间、当前发奖状态（AwardState）等。
     */
    private UserAwardRecordEntity userAwardRecordEntity;

    /**
     * 消息补偿任务实体
     * 承载通信保障数据，如：消息Topic、JSON格式的消息体负载、任务执行状态（TaskState）等。
     */
    private TaskEntity taskEntity;

}