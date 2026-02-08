package com.c.domain.award.model.entity;

import com.c.domain.award.model.vo.AwardStateVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 用户中奖记录实体
 * 作用：描述用户参与活动产生的中奖凭证，支撑后续异步发奖及中奖详情展示。
 *
 * @author cyh
 * @date 2026/02/01
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserAwardRecordEntity {

    /** 用户唯一 ID */
    private String userId;

    /** 活动 ID */
    private Long activityId;

    /** 抽奖策略 ID */
    private Long strategyId;

    /** 抽奖订单 ID：全链路幂等校验的核心键，关联活动参与单据 */
    private String orderId;

    /** 奖品 ID */
    private Integer awardId;

    /** 奖品发放配置（如积分值、面额等 JSON 配置） */
    private String awardConfig;

    /** 奖品标题：中奖时刻的奖品名称快照 */
    private String awardTitle;

    /** 中奖时间 */
    private Date awardTime;

    /** 奖品发放状态 */
    private AwardStateVO awardState;

}