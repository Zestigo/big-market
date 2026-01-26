package com.c.infrastructure.po;

import lombok.Data;
import java.util.Date;

/**
 * 用户活动账户流水持久化对象
 * 职责：记录用户账户额度的每一次变动（增加或消耗），用于审计、对账及幂等性校验。
 *
 * @author cyh
 * @date 2026/01/25
 */
@Data
public class RaffleActivityAccountFlow {

    /** 自增ID */
    private Integer id;

    /** 用户ID */
    private String userId;

    /** 活动ID */
     private Long activityId;

     /** 本次流水变更的总次数额度 */
    private Integer totalCount;

    /** 本次流水变更的日次数额度 */
    private Integer dayCount;

    /** 本次流水变更的月次数额度 */
    private Integer monthCount;

    /** 流水ID（生成的唯一业务序列号，用于幂等控制） */
    private String flowId;

    /** 流水渠道（activity-活动领取、sale-购买、redeem-兑换、free-免费赠送） */
    private String flowChannel;

    /** 业务ID（外部透传，如订单ID，确保同一业务不重复加次数） */
    private String bizId;

    /** 创建时间 */
    private Date createTime;

    /** 更新时间 */
    private Date updateTime;

}