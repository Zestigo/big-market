package com.c.domain.strategy.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author cyh
 * @description 规则物料实体：封装决策引擎执行时所需的上下文参考信息
 * @date 2026/01/18
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RuleMatterEntity {

    /** 用户唯一标识：用于校验用户抽奖资格或频次限制 */
    private String userId;

    /** 策略唯一标识：关联具体的抽奖方案配置 */
    private Long strategyId;

    /** 抽奖奖品ID：若规则作用于策略层面（如前置拦截），此字段可不传 */
    private Integer awardId;

    /**
     * 抽奖规则模型标识
     * 常用模型示例：
     * 1. rule_random：随机概率计算规则
     * 2. rule_lock：基于累计抽奖次数的解锁规则
     * 3. rule_luck_award：用于库存不足时的兜底保障规则
     */
    private String ruleModel;

}