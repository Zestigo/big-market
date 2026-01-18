package com.c.domain.strategy.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data // Lombok：自动生成get/set/toString/hashCode等
@Builder // 建造者模式：方便快速创建对象（如 StrategyAwardEntity.builder().strategyId(1L).build()）
@AllArgsConstructor // 全参构造
@NoArgsConstructor // 无参构造
public class RaffleFactorEntity {
    /** 用户ID */
    private String userId;
    /** 策略ID */
    private Long strategyId;
}
