package com.c.types.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 全局响应码枚举
 * 涵盖：基础响应、系统治理、策略装配、活动生命周期、库存、账户额度、抽奖订单、积分交易
 *
 * @author cyh
 * @date 2026/03/11
 */
@AllArgsConstructor
@Getter
public enum ResponseCode {

    /* --- 基础响应 --- */
    SUCCESS("0000", "调用成功"),
    UN_ERROR("0001", "调用失败"),
    ILLEGAL_PARAMETER("0002", "非法参数"),
    INDEX_DUP("0003", "唯一索引冲突"),

    /* --- 系统治理 --- */
    DEGRADE_SWITCH("ERR_SYS_001", "抽奖活动火爆，系统触发降级保护，请稍后再试"),
    RATE_LIMITER("ERR_SYS_002", "访问过于频繁，系统限流中"),
    HYSTRIX("ERR_SYS_003", "系统繁忙，访问触发熔断拦截"),

    /* --- 策略装配 --- */
    STRATEGY_RULE_WEIGHT_IS_NULL("ERR_BIZ_001", "策略权重规则已启用但未配置"),
    UN_ASSEMBLED_STRATEGY_ARMORY("ERR_BIZ_002", "抽奖策略未装配，请先执行装配操作"),

    /* --- 活动生命周期 --- */
    ACTIVITY_STATE_ERROR("ERR_BIZ_003", "活动未开启"),
    ACTIVITY_DATE_ERROR("ERR_BIZ_004", "当前不在活动有效日期范围内"),
    STRATEGY_CONFIG_ERROR("ERR_BIZ_015", "抽奖策略配置异常，概率总和或最小概率不符合要求"),

    /* --- 库存模块 --- */
    ACTIVITY_SKU_STOCK_ERROR("ERR_BIZ_005", "活动SKU库存不足"),
    STRATEGY_AWARD_STOCK_EMPTY("ERR_BIZ_010", "奖品库存已耗尽"),

    /* --- 账户额度 --- */
    ACCOUNT_QUOTA_ERROR("ERR_BIZ_006", "账户总额度不足"),
    ACCOUNT_MONTH_QUOTA_ERROR("ERR_BIZ_007", "账户月额度不足"),
    ACCOUNT_DAY_QUOTA_ERROR("ERR_BIZ_008", "账户日额度不足"),

    /* --- 抽奖订单 --- */
    ACTIVITY_ORDER_ERROR("ERR_BIZ_009", "该抽奖单已使用，无法重复抽奖"),
    ACTIVITY_NOT_EXIST("ERR_BIZ_011", "活动不存在或已下架"),

    /* --- 积分交易 --- */
    CREDIT_ACCOUNT_LOCK_ERROR("ERR_BIZ_012", "账户处理中，请稍后重试"),
    CREDIT_BALANCE_INSUFFICIENT("ERR_BIZ_013", "积分余额不足"),
    CREDIT_ORDER_ALREADY_EXISTS("ERR_BIZ_014", "交易单号已存在，请勿重复提交"),
    ;

    // 状态码：用于系统间逻辑判定
    private final String code;
    // 提示信息：用于前端展示/日志说明
    private final String info;

}