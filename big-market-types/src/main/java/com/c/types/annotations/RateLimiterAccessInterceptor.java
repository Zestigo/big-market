package com.c.types.annotations;

import java.lang.annotation.*;

/**
 * 限流拦截注解
 * 通过 AOP 拦截方法调用，提供限流控制、黑名单锁定及降级逻辑。
 *
 * @author cyh
 * @date 2026/02/21
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface RateLimiterAccessInterceptor {

    /* 拦截标识：用于提取请求特征（如 userId, ip）。支持对象属性名提取，默认为 "all" 全量拦截 */
    String key() default "all";

    /* 限制频次：每秒允许通过的请求次数 (QPS)。默认 1.0 表示每秒 1 次 */
    double permitsPerSecond() default 1.0;

    /* 黑名单阈值：单用户/IP 连续触发限流的次数。达到此值后加入分布式黑名单。0 表示关闭黑名单 */
    int blacklistCount() default 0;

    /* 降级方法名：触发限流或黑名单时，反射执行的备选逻辑方法。注意：方法签名需与原方法保持一致 */
    String fallbackMethod() default "";

}