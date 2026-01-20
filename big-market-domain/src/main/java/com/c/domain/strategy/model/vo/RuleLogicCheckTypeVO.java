package com.c.domain.strategy.model.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 规则逻辑检查类型 VO
 *
 * @author cyh
 * @date 2026/01/19
 */
@Getter
@AllArgsConstructor
public enum RuleLogicCheckTypeVO {
    ALLOW("0000", "放行；执行后续流程，不受规则引擎的影响"),
    TAKE_OVER("0001", "接管；后续的流程，受规则引擎执行结果的影响"),
    ;

    private final String code;
    private final String info;
}
