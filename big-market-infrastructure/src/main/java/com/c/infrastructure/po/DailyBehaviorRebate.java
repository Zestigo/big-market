package com.c.infrastructure.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 每日行为返利持久层对象
 * * 关联数据库表：daily_behavior_rebate
 * 业务描述：用于配置用户完成特定行为（如签到、支付）后的返利规则。
 *
 * @author cyh
 * @since 2026/02/05
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyBehaviorRebate {

    /** 自增主键 ID */
    private Long id;

    /** 行为类型标识 可选值：sign (签到)、openai_pay (支付) */
    private String behaviorType;

    /** 返利业务描述 */
    private String rebateDesc;

    /** 返利奖品类型 可选值：sku (活动商品)、integral (用户积分) */
    private String rebateType;

    /** 返利详细配置 建议存储格式：JSON 字符串，具体结构由 rebateType 决定 */
    private String rebateConfig;

    /** 规则启用状态 可选值：OPEN (开启)、CLOSE (关闭) */
    private String state;

    /** 创建时间 */
    private Date createTime;

    /** 更新时间 */
    private Date updateTime;

}