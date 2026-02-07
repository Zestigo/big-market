package com.c.infrastructure.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 持久化对象：抽奖策略主配置 (strategy)
 * <p>
 * 职责：映射数据库 strategy 表，定义抽奖逻辑的核心配置。
 * 作用：承载策略 ID 与其挂载的各种规则模型（如黑名单、权重、抽奖次数限制等）的关联关系。
 *
 * @author cyh
 * @date 2026/02/06
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Strategy {

    /** 自增 ID：数据库物理主键 */
    private Long id;

    /** 策略 ID：业务层面的唯一标识（如：10001L），用于在抽奖时指定使用的策略 */
    private Long strategyId;

    /** 策略描述：用于运营及后台识别该抽奖策略的设计意图 */
    private String strategyDesc;

    /** 规则模型组合：以逗号分隔的规则 Key 集合（如：rule_blacklist,rule_weight,rule_luck_award）。 */
    private String ruleModels;

    /** 创建时间 */
    private Date createTime;

    /** 更新时间 */
    private Date updateTime;

}