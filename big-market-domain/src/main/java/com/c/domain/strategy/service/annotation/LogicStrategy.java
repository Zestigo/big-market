package com.c.domain.strategy.service.annotation;

import com.c.domain.strategy.service.rule.factory.DefaultLogicFactory;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 逻辑策略
 * 增加自定义的注解，方便对象注入
 *
 * @author cyh
 * @date 2026/01/17
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface LogicStrategy {
    DefaultLogicFactory.LogicModel logicMode();
}
