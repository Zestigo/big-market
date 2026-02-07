package com.c.domain.award.model.entity;

import com.c.domain.award.model.vo.AwardStateVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 用户中奖记录实体对象（领域对象）
 * 职责：描述用户在参与抽奖活动中产生的具体获奖凭证。
 * 作用：承载中奖后的核心上下文信息，支撑后续的异步发奖逻辑、幂等校验及中奖详情展示。
 * 核心：通过 orderId 建立与活动抽奖单的强关联，保障全局业务的一致性。
 *
 * @author cyh
 * @date 2026/02/01
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserAwardRecordEntity {

    /** 用户唯一 ID：标识中奖归属方 */
    private String userId;

    /** 活动 ID：标识产生中奖记录的具体活动 */
    private Long activityId;

    /** 抽奖策略 ID：记录本次中奖所执行的策略逻辑 */
    private Long strategyId;

    /** 抽奖订单 ID：由活动参与流程产生的唯一业务流水号，作为全链路幂等校验的核心键（Idempotency Key） */
    private String orderId;

    /** 奖品 ID：标识中奖奖品的唯一编号 */
    private Integer awardId;

    /** 奖品标题：中奖时的奖品名称快照，用于前端展示与日志追溯 */
    private String awardTitle;

    /** 中奖时间：用户触发中奖动作的精确时刻 */
    private Date awardTime;

    /** 奖品状态：描述奖品发放的实时进度（CREATE-待发奖、COMPLETED-发奖完成、FAIL-发奖失败） */
    private AwardStateVO awardState;

}