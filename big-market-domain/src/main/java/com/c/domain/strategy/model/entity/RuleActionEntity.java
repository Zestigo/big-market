package com.c.domain.strategy.model.entity;

import com.c.domain.strategy.model.vo.RuleLogicCheckTypeVO;
import lombok.*;

@Data // Lombok：自动生成get/set/toString/hashCode等
@Builder // 建造者模式：方便快速创建对象（如 StrategyAwardEntity.builder().strategyId(1L).build()）
@AllArgsConstructor // 全参构造
@NoArgsConstructor // 无参构造
public class RuleActionEntity<T extends RuleActionEntity.RaffleEntity> {
    private String code = RuleLogicCheckTypeVO.ALLOW.getCode();
    private String info = RuleLogicCheckTypeVO.ALLOW.getInfo();
    private String ruleModel;
    private T data;

    static public class RaffleEntity {

    }

    // 抽奖前
    @EqualsAndHashCode(callSuper = true)
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    static public class RaffleBeforeEntity extends RaffleEntity {
        // 策略ID
        private Long strategyId;
        // 权重值Key；用于抽奖时可以选择权重抽奖。
        private String ruleWeightValueKey;
        // 奖品ID
        private Integer awardId;
    }
}
