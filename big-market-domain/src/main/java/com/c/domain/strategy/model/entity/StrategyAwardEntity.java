package com.c.domain.strategy.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data // Lombok：自动生成get/set/toString/hashCode等
@Builder // 建造者模式：方便快速创建对象（如 StrategyAwardEntity.builder().strategyId(1L).build()）
@AllArgsConstructor // 全参构造
@NoArgsConstructor // 无参构造
public class StrategyAwardEntity {
    private Long strategyId; // 策略ID（比如“春节抽奖活动”的策略ID）
    private Integer awardId; // 奖项ID（比如1=一等奖，2=二等奖）
    private Integer awardCount; // 奖项总数量（比如一等奖总共10个）
    private Integer awardCountSurplus; // 奖项剩余数量（比如一等奖还剩3个）
    private BigDecimal awardRate; // 奖项概率（比如0.05=5%中奖概率）
}