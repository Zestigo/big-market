package com.c.types.annotations;

import java.lang.annotation.*;

/**
 * DCC 统一前缀与环境配置
 *
 * @author cyh
 * @date 2026/02/21
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DCCConfiguration {

    /* 全局前缀，如 "cyh:microservice:order" */
    String prefix() default "";

    /* 类级别默认 DataID */
    String dataId() default "";

    /* 类级别默认分组 */
    String group() default "DEFAULT_GROUP";
}