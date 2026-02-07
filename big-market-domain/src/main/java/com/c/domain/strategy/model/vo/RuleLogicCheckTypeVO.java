package com.c.domain.strategy.model.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 值对象：规则逻辑校验决策类型
 * <p>
 * 职责：定义规则引擎执行后的决策指令，决定当前抽奖链路是继续执行还是被规则结果接管。
 * 作用：支撑抽奖策略中的“前置/中置/后置”规则处理逻辑。
 *
 * @author cyh
 * @date 2026/01/19
 */
@Getter
@AllArgsConstructor
public enum RuleLogicCheckTypeVO {

    /**
     * 放行 (Allow)
     * 含义：当前规则校验通过，允许执行后续的抽奖流程。
     * 场景：如“黑名单检查通过”、“权重校验不匹配”时，继续走常规抽奖逻辑。
     */
    ALLOW("0000", "放行"),

    /**
     * 接管 (Take Over)
     * 含义：当前规则产生确定的结果，后续流程必须受此规则执行结果的影响（通常是终止后续流程）。
     * 场景：如“触发黑名单直接拒绝”、“触发权重直接指定中奖项”等。
     */
    TAKE_OVER("0001", "接管");

    /** 决策编码：对应规则执行后的状态标识 */
    private final String code;

    /** 决策描述信息 */
    private final String info;

    /**
     * 静态方法：通过编码快速还原枚举决策
     *
     * @param code 决策编码
     * @return 匹配的枚举对象，未匹配返回 null
     */
    public static RuleLogicCheckTypeVO fromCode(String code) {
        for (RuleLogicCheckTypeVO type : RuleLogicCheckTypeVO.values()) {
            if (type
                    .getCode()
                    .equals(code)) {
                return type;
            }
        }
        return null;
    }

}