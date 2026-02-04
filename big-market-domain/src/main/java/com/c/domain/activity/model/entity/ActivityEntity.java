package com.c.domain.activity.model.entity;

import com.c.domain.activity.model.vo.ActivityStateVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 活动配置领域实体对象
 * 封装抽奖活动的核心元数据，包括生命周期、关联策略及次数门槛。
 *
 * @author cyh
 * @since 2026/02/04
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ActivityEntity {

    /** 活动唯一标识 ID */
    private Long activityId;

    /** 活动名称（用于展示在 C 端页面或后台清单） */
    private String activityName;

    /** 活动描述（详细规则、活动说明等） */
    private String activityDesc;

    /** 活动开始时间：早于此时间用户无法看到或参与活动 */
    private Date beginDateTime;

    /** 活动结束时间：晚于此时间活动自动失效，库存将停止扣减 */
    private Date endDateTime;

    /** 活动参与次数配置 ID： 关联 raffle_activity_count 表，定义用户可抽奖的总次数、日次数及月次数。 */
    private Long activityCountId;

    /** 抽奖策略 ID：决定了该活动使用哪套职责链、中奖概率以及规则树。 */
    private Long strategyId;

    /** 活动当前状态，枚举：create-创建、open-开启、close-关闭。 */
    private ActivityStateVO state;

}