package com.c.domain.strategy.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data // Lombok：自动生成get/set/toString/hashCode等
@Builder // 建造者模式：方便快速创建对象（如 StrategyAwardEntity.builder().strategyId(1L).build()）
@AllArgsConstructor // 全参构造
@NoArgsConstructor // 无参构造
public class RuleMatterEntity {
    /** 用户ID */
    private String userId;
    /** 策略ID */
    private Long strategyId;
    /** 抽奖奖品ID【规则类型为策略，则不需要奖品ID】 */
    private Integer awardId;
    /** 抽奖规则类型【rule_random - 随机值计算、rule_lock - 抽奖几次后解锁、rule_luck_award - 幸运奖(兜底奖品)】 */
    private String ruleModel;
}
