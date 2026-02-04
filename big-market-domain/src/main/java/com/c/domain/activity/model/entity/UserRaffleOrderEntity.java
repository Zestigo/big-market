package com.c.domain.activity.model.entity;

import com.c.domain.activity.model.vo.UserRaffleOrderStateVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 用户抽奖订单实体对象
 * 该实体对应数据库中的抽奖单据记录，是用户获得参与资格后的资产凭证。
 *
 * @author cyh
 * @since 2026/02/04
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserRaffleOrderEntity {

    /** 用户唯一标识 */
    private String userId;

    /** 活动唯一标识 */
    private Long activityId;

    /** 活动名称（冗余字段，方便前端展示或流水核对） */
    private String activityName;

    /** 抽奖策略 ID（关联 strategy 领域执行具体抽奖算法） */
    private Long strategyId;

    /** 订单 ID（全局唯一标识，通常用于幂等性校验） */
    private String orderId;

    /** 下单时间（记录订单生成的物理时刻） */
    private Date orderTime;

    /** 订单状态；create-创建、used-已使用、cancel-已作废 */
    private UserRaffleOrderStateVO orderState;

    /** 活动结束时间（用于控制该订单在缓存或库中的生命周期） */
    private Date endDateTime;

}