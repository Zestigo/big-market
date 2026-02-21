package com.c.domain.activity.model.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 用户抽奖订单状态枚举
 *
 * @author cyh
 * @date 2026/02/21
 */
@Getter
@AllArgsConstructor
public enum UserRaffleOrderStateVO {

    /* 创建 */
    CREATE("create", "创建"),

    /* 已使用 */
    USED("used", "已使用"),

    /* 已作废 */
    CANCEL("cancel", "已作废");

    /* 状态编码 */
    private final String code;

    /* 状态描述 */
    private final String desc;

    /**
     * 自定义解析方法：根据 code 匹配枚举
     * 解决 valueOf 严格区分大小写且仅匹配变量名的问题
     */
    public static UserRaffleOrderStateVO fromCode(String code) {
        for (UserRaffleOrderStateVO state : values()) {
            if (state
                    .getCode()
                    .equalsIgnoreCase(code)) {
                return state;
            }
        }
        return null; // 或者抛出自定义异常
    }
}