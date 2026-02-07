package com.c.domain.award.model.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 任务状态值对象
 * 遵循 Java 大写常量规范，同时 Code 对应数据库小写存储
 *
 * @author cyh
 * @date 2026/02/01
 */
@Getter
@AllArgsConstructor
public enum TaskStateVO {

    /** 初始创建状态 */
    CREATE("create", "已创建"),

    /** 投递成功终态 */
    COMPLETED("completed", "发送完成"),

    /** 投递失败状态 */
    FAIL("fail", "发送失败");

    /** 状态编码（存入数据库的值，建议小写） */
    private final String code;

    /** 状态描述 */
    private final String desc;

}