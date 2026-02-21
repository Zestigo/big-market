package com.c.types.annotations;

import java.lang.annotation.*;

/**
 * 动态配置中心（DCC）注入注解
 *
 * @author cyh
 * @date 2026/02/21
 */
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
@Documented
public @interface DCCValue {

    /* 配置项 Key，支持 "key:defaultValue" */
    /* 若有类前缀，自动拼接为 prefix + "." + value */
    String value() default "";

    /* 指定 DataID */
    /* 若此处为空，则尝试取类注解的 DataID；若都为空，则取 value(Key) 作为 DataID */
    String dataId() default "";

    /* 配置分组 */
    String group() default "";

    /* 配置项描述 */
    String description() default "";
}