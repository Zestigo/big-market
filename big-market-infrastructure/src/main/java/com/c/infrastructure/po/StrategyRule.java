package com.c.infrastructure.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 策略规则持久化对象 (Strategy Rule Persistent Object)
 * * 职责定位：
 * 存储抽奖过程中各类逻辑校验的配置参数。它决定了抽奖流程在职责链节点（Chain）或
 * 决策树节点（Tree）中的流转行为。
 *
 * @author cyh
 * @date 2026/02/01
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StrategyRule {

    /** 自增 ID */
    private Long id;

    /** 策略 ID（关联 strategy 表） */
    private Long strategyId;

    /** 奖品 ID
     * 注：若该字段为空，表示此规则属于“策略级规则”（如：黑名单、抽奖前权重）；
     * 若不为空，则属于“奖品级规则”（如：奖品解锁次数限制）。
     */
    private Integer awardId;

    /** 规则类型；1-抽奖前规则 (rule_pre)、2-抽奖中规则 (rule_in)、3-抽奖后规则 (rule_post) */
    private Integer ruleType;

    /** 规则模型标识；用于关联特定的逻辑处理器
     * 示例：rule_random（随机）、rule_lock（抽奖次数锁）、rule_luck_award（兜底奖品）
     */
    private String ruleModel;

    /** * 规则配置值；具体业务逻辑的参数化表达。
     * * 不同模型的配置格式示例：
     * 1. 权重模型 (rule_weight)："100:1,2,3 200:4,5,6" (积分:奖品ID列表)
     * 2. 次数锁模型 (rule_lock)："10" (抽奖满10次解锁)
     * 3. 黑名单模型 (rule_blacklist)："user01,user02" (被拦截的用户ID)
     */
    private String ruleValue;

    /** 规则描述（用于后台管理系统识别规则用途） */
    private String ruleDesc;

    /** 创建时间 */
    private Date createTime;

    /** 更新时间 */
    private Date updateTime;
}