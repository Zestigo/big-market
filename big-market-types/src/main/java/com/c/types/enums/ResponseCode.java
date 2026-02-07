package com.c.types.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 全局响应码枚举
 * 职责：定义系统级、业务级所有异常状态码，支撑 Repository 及领域层的异常抛出
 */
@AllArgsConstructor
@Getter
public enum ResponseCode {

    // --- 基础异常 (0xxx) ---
    SUCCESS("0000", "调用成功"),
    UN_ERROR("0001", "调用失败"),
    ILLEGAL_PARAMETER("0002", "非法参数"),
    INDEX_DUP("0003", "唯一索引冲突"),

    // --- 策略与装配异常 (ERR_BIZ_001 ~ 002) ---
    STRATEGY_RULE_WEIGHT_IS_NULL("ERR_BIZ_001", "业务异常，策略规则中 rule_weight 权重规则已适用但未配置"),
    UN_ASSEMBLED_STRATEGY_ARMORY("ERR_BIZ_002", "抽奖策略配置未装配，请通过IStrategyArmory完成装配"),

    // --- 活动状态与时间异常 (ERR_BIZ_003 ~ 004) ---
    ACTIVITY_STATE_ERROR("ERR_BIZ_003", "活动未开启（非open状态）"),
    ACTIVITY_DATE_ERROR("ERR_BIZ_004", "非活动日期范围"),

    // --- 库存相关异常 (ERR_BIZ_005, 010) ---
    ACTIVITY_SKU_STOCK_ERROR("ERR_BIZ_005", "活动SKU库存不足"),
    STRATEGY_AWARD_STOCK_EMPTY("ERR_BIZ_010", "策略奖品库存已耗尽"),

    // --- 账户额度异常 (ERR_BIZ_006 ~ 008) ---
    ACCOUNT_QUOTA_ERROR("ERR_BIZ_006", "账户总额度不足"),
    ACCOUNT_MONTH_QUOTA_ERROR("ERR_BIZ_007", "账户月额度不足"),
    ACCOUNT_DAY_QUOTA_ERROR("ERR_BIZ_008", "账户日额度不足"),

    // --- 订单与单据异常 (ERR_BIZ_009, 011) ---
    ACTIVITY_ORDER_ERROR("ERR_BIZ_009", "用户抽奖单已使用过，不可重复抽奖"),
    ACTIVITY_NOT_EXIST("ERR_BIZ_011", "活动不存在或已下架"),
    ;

    private final String code;
    private final String info;

}