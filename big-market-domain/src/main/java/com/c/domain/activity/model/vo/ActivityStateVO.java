package com.c.domain.activity.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;


/**
 * @author cyh
 * @date 2026/01/27
 */
@Getter
@AllArgsConstructor
public enum ActivityStateVO {

    create("create", "创建"), open("open", "开启"), close("close", "关闭"),
    ;

    private final String code;
    private final String desc;

}
