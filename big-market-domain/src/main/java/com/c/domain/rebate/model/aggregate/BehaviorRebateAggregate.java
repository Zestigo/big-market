package com.c.domain.rebate.model.aggregate;

import com.c.domain.rebate.model.entity.BehaviorRebateOrderEntity;
import com.c.domain.rebate.model.entity.TaskEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 行为返利聚合根
 *
 * @author cyh
 * @date 2026/02/05
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BehaviorRebateAggregate {

    /** 用户唯一ID */
    private String userId;

    /** 行为返利订单流水实体 */
    private BehaviorRebateOrderEntity behaviorRebateOrderEntity;

    /** 补偿任务实体（用于可靠性发送） */
    private TaskEntity taskEntity;

}