package com.c.infrastructure.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 持久化对象：策略奖项明细配置 (strategy_award)
 * 职责：映射数据库 strategy_award 表，定义特定抽奖策略下的奖品构成及中奖参数。
 * 作用：支撑抽奖过程中的概率计算、库存扣减检查以及前端奖池列表的展示。
 *
 * @author cyh
 * @date 2026/02/06
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StrategyAward {

    /** 自增 ID：数据库物理主键 */
    private Long id;

    /** 策略 ID：归属的抽奖策略唯一标识 */
    private Long strategyId;

    /** 奖品 ID：具体奖品项的唯一标识 */
    private Integer awardId;

    /** 奖品标题：中奖时展示给用户的名称（如：100元京东卡） */
    private String awardTitle;

    /** 奖品副标题：辅助描述（如：满200可用） */
    private String awardSubtitle;

    /** 奖品原始总库存：该策略下配置的初始发放总量 */
    private Integer awardCount;

    /** 奖品剩余库存：实时扣减后的可用发放数量 */
    private Integer awardCountSurplus;

    /** 中奖概率：用于概率算法计算的权重值（如：0.005 代表 0.5%） */
    private BigDecimal awardRate;

    /**
     * 奖项自定义规则：如 "rule_luck_award"（兜底奖品规则）。
     * 逻辑：当该奖项在特定条件下触发（如必中、抽奖N次后解锁）时执行的过滤模型。
     */
    private String ruleModels;

    /** 排序：定义前端奖池列表的展示顺序 */
    private Integer sort;

    /** 创建时间 */
    private Date createTime;

    /** 更新时间 */
    private Date updateTime;

}