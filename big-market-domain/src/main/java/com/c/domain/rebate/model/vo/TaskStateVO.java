package com.c.domain.rebate.model.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 任务状态值对象枚举
 *
 * @author cyh
 * @date 2026/02/05
 */
@Getter
@AllArgsConstructor
public enum TaskStateVO {

    /** 任务初始创建（待处理） */
    CREATE("create", "创建"),

    /** 任务执行成功（已完成） */
    COMPLETE("complete", "发送完成"),

    /** 任务执行失败（需补偿） */
    FAIL("fail", "发送失败"),
    ;

    /** 状态编码标识 */
    private final String code;

    /** 状态含义描述 */
    private final String desc;

}