package com.c.domain.award.model.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @description 账户状态枚举值对象
 * @author cyh
 * @date 2026/02/07
 */
@Getter
@AllArgsConstructor
public enum AccountStatusVO {

    OPEN("open", "开启"),
    CLOSE("close", "冻结"),
    ;

    /** 状态代码 */
    private final String code;

    /** 状态描述 */
    private final String desc;

}