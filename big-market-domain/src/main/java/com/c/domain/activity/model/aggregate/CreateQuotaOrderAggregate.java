package com.c.domain.activity.model.aggregate;

import com.c.domain.activity.model.entity.ActivityOrderEntity;
import com.c.domain.activity.model.vo.OrderStateVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建活动额度订单聚合根
 * 封装活动订单流水与账户额度变更，确保仓储层在同一事务内完成订单插入与额度更新。
 *
 * @author cyh
 * @date 2026/01/27
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CreateQuotaOrderAggregate {

    /** 用户唯一标识 */
    private String userId;

    /** 活动唯一标识 */
    private Long activityId;

    /** 账户限额变更值 - 总次数 */
    private Integer totalCount;

    /** 账户限额变更值 - 日次数 */
    private Integer dayCount;

    /** 账户限额变更值 - 月次数 */
    private Integer monthCount;

    /** 活动订单流水实体 */
    private ActivityOrderEntity activityOrderEntity;

    /**
     * 更新订单状态
     *
     * @param orderState 目标订单状态枚举
     */
    public void setOrderState(OrderStateVO orderState) {
        if (null != this.activityOrderEntity) {
            this.activityOrderEntity.setState(orderState);
        }
    }

}