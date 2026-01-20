package com.c.domain.strategy.model.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 规则对比限制类型枚举（Value Object）
 * 用于决策树引擎中，判断节点输出值与连线条件之间的逻辑关系。
 *
 * @author cyh
 * @date 2026/01/19
 */
@Getter
@AllArgsConstructor
public enum RuleLimitTypeVO {

    /** 等于：输入值与预期值完全匹配 */
    EQUAL(1, "等于"),

    /** 大于：输入值数字量级超过预期值 */
    GT(2, "大于"),

    /** 小于：输入值数字量级低于预期值 */
    LT(3, "小于"),

    /** 大于等于：输入值数字量级不低于预期值 */
    GE(4, "大于&等于"),

    /** 小于等于：输入值数字量级不超过预期值 */
    LE(5, "小于&等于"),

    /** 枚举：输入值包含在预设的集合范围内 */
    ENUM(6, "枚举"),
    ;

    /** 类型编码 */
    private final Integer code;

    /** 类型描述 */
    private final String info;

}