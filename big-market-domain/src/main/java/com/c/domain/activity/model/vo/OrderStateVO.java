package com.c.domain.activity.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;


/**
 *
 * @author cyh
 * @date 2026/01/27
 */
@Getter
@AllArgsConstructor
public enum OrderStateVO {

    completed("completed", "完成");

    private final String code;
    private final String desc;

}
