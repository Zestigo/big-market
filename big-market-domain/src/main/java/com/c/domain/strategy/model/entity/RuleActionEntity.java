package com.c.domain.strategy.model.entity;

import com.c.domain.strategy.model.vo.RuleLogicCheckTypeVO;
import lombok.*;

/**
 * @author cyh
 * @description 规则动作实体：用于承载规则过滤后的执行结果及其后续动作数据
 * @date 2026/02/02
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RuleActionEntity<T extends RuleActionEntity.RaffleEntity> {

    /** 结果码：默认放行 {@link RuleLogicCheckTypeVO#ALLOW} */
    private String code = RuleLogicCheckTypeVO.ALLOW.getCode();

    /** 结果说明 */
    private String info = RuleLogicCheckTypeVO.ALLOW.getInfo();

    /** 规则模型标识（如：rule_lock, rule_luck_award） */
    private String ruleModel;

    /** 规则执行后携带的业务数据（泛型扩展） */
    private T data;

    /**
     * 抽奖实体基类，作为泛型约束
     */
    static public class RaffleEntity {
    }

    /**
     * 抽奖前置规则动作实体
     * 场景：用于处理权重、黑名单等拦截或重定向逻辑后的数据承载
     */
    @EqualsAndHashCode(callSuper = true)
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    static public class RaffleBeforeEntity extends RaffleEntity {
        /** 策略ID */
        private Long strategyId;

        /** 权重值Key：用于定位具体的抽奖权重范围（如：6000积分对应的奖品池） */
        private String ruleWeightValueKey;

        /** 奖品ID：规则执行过程中预设或锁定的奖品 */
        private Integer awardId;
    }
}