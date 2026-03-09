package com.c.aop;

import com.c.types.annotations.DCCConfiguration;
import com.c.types.annotations.DCCValue;
import com.c.types.annotations.RateLimiterAccessInterceptor;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.util.concurrent.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 限流切面拦截器 —— 核心防御组件
 * 核心逻辑：分级流控
 * - 识别成功：按业务注解 QPS 限制
 * - 识别失败：按全局兜底 QPS 强制限制，防止防御被绕过
 *
 * @author cyh
 * @date 2026/02/21
 */
@Slf4j
@Aspect
@Component
@DCCConfiguration(dataId = "rate-limiter.yaml", prefix = "rateLimiter")
public class RateLimiterAOP {

    /** Redis 违规计数命名空间前缀 */
    private static final String BLACKLIST_PREFIX = "rate_limiter_blacklist:";

    /** 全局兜底桶的唯一标识（解析不到业务标识时使用） */
    private static final String DEFAULT_BUCKET_KEY = "global_fallback_bucket";

    /** 限流总开关（DCC动态配置，默认关闭） */
    @DCCValue("switch:false")
    private boolean isEnable;

    /** 黑名单封禁时长（单位：小时，DCC动态配置，默认24） */
    @DCCValue("blacklistExpire:24")
    private int blacklistExpire;

    /** 全局兜底阈值：解析不到标识时的默认QPS（DCC配置，默认1.0） */
    @DCCValue("defaultThreshold:1.0")
    private double defaultThreshold;

    /** 分布式状态共享客户端（Redisson） */
    @Resource
    private RedissonClient redissonClient;

    /** 本地限流器缓存：LRU回收，1分钟过期，防止内存溢出 */
    private final Cache<String, RateLimiter> loginRecord = CacheBuilder
            .newBuilder()
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .build();

    /**
     * 切点定义
     * 匹配所有标注了 {@link RateLimiterAccessInterceptor} 注解的方法
     */
    @Pointcut("@annotation(com.c.types.annotations.RateLimiterAccessInterceptor)")
    public void aopPoint() {
    }

    /**
     * 环绕增强：实现分级流控+黑名单封禁的核心逻辑
     *
     * @param jp                           切面连接点，包含目标方法、参数等信息
     * @param rateLimiterAccessInterceptor 限流注解实例，包含QPS、黑名单阈值等配置
     * @return 目标方法执行结果 或 降级方法结果
     * @throws Throwable 执行过程中抛出的异常（目标方法/降级方法异常）
     */
    @Around("aopPoint() && @annotation(rateLimiterAccessInterceptor)")
    public Object doRouter(ProceedingJoinPoint jp, RateLimiterAccessInterceptor rateLimiterAccessInterceptor) throws Throwable {
        // 1. DCC 动态开关检查：开关关闭则直接放行
        if (!isEnable) {
            return jp.proceed();
        }

        // 2. 身份标识解析：从方法参数中提取注解指定的标识字段
        String key = rateLimiterAccessInterceptor.key();
        String keyAttr = getAttrValue(key, jp.getArgs());

        // 差异化标识处理：解析失败则指向全局兜底桶
        boolean isFallback = StringUtils.isBlank(keyAttr) || "all".equals(keyAttr);
        if (isFallback) {
            keyAttr = DEFAULT_BUCKET_KEY;
            log.warn("DCC 拦截警告：标识解析失败，流量已被导流至低QPS兜底桶");
        }

        // 3. 黑名单前置过滤：达到阈值则直接降级
        if (rateLimiterAccessInterceptor.blacklistCount() != 0) {
            RAtomicLong blackCount = redissonClient.getAtomicLong(BLACKLIST_PREFIX + keyAttr);
            if (blackCount.get() >= rateLimiterAccessInterceptor.blacklistCount()) {
                log.warn("DCC 封禁拦截：{} 触发黑名单策略，当前计数值 {}", keyAttr, blackCount.get());
                return fallbackMethodResult(jp, rateLimiterAccessInterceptor.fallbackMethod());
            }
        }

        // 4. 获取/创建令牌桶：差异化QPS（业务QPS/兜底QPS）
        RateLimiter rateLimiter = loginRecord.get(keyAttr, () -> {
            double qps = isFallback ? defaultThreshold : rateLimiterAccessInterceptor.permitsPerSecond();
            return RateLimiter.create(qps);
        });

        // 5. 令牌申请与处罚：申请失败则累加黑名单计数并降级
        if (!rateLimiter.tryAcquire()) {
            if (rateLimiterAccessInterceptor.blacklistCount() != 0) {
                RAtomicLong blackCount = redissonClient.getAtomicLong(BLACKLIST_PREFIX + keyAttr);
                long currentCount = blackCount.incrementAndGet();
                if (currentCount == 1) {
                    blackCount.expire(Duration.ofHours(blacklistExpire));
                }
            }
            log.info("DCC 限流触发：{} 令牌不足", keyAttr);
            return fallbackMethodResult(jp, rateLimiterAccessInterceptor.fallbackMethod());
        }

        // 6. 令牌申请成功：执行业务代码
        return jp.proceed();
    }

    /**
     * 降级回调处理：执行指定的降级方法并返回结果
     *
     * @param jp             切面连接点，用于获取目标类实例、方法参数等
     * @param fallbackMethod 降级方法名称
     * @return 降级方法执行结果
     * @throws Exception 反射获取/执行降级方法时的异常
     */
    private Object fallbackMethodResult(JoinPoint jp, String fallbackMethod) throws Exception {
        // 1. 获取原方法的规格：包括参数的个数和类型
        Signature sig = jp.getSignature();
        MethodSignature methodSignature = (MethodSignature) sig;

        // 2. 匹配备胎方法：在目标类中寻找一个名字和参数规格都完全一致的方法
        // 注意：这里的 getMethod 只能找到 public 修饰的方法
        Method method = jp
                .getTarget()
                .getClass()
                .getMethod(fallbackMethod, methodSignature.getParameterTypes());

        // 3. 正式反射调用：让 Spring 代理对象执行这个降级方法，并传入原始参数
        // jp.getThis() 确保了执行过程中降级方法上的其他 AOP 逻辑（如日志）依然有效
        return method.invoke(jp.getThis(), jp.getArgs());
    }

    /**
     * 属性解析工具：从方法参数中提取指定名称的字段值
     *
     * @param attr 要提取的字段名称
     * @param args 方法参数数组
     * @return 字段值（字符串格式），解析失败返回null
     */
    public String getAttrValue(String attr, Object[] args) {
        if (args == null || args.length == 0 || StringUtils.isBlank(attr)) return null;
        // 特殊处理：第一个参数是字符串且字段名是userId时直接返回
        if (args[0] instanceof String && attr.equals("userId")) {
            return args[0].toString();
        }
        // 遍历参数，反射提取指定字段值
        for (Object arg : args) {
            Object value = getValueByName(arg, attr);
            if (value != null) return String.valueOf(value);
        }
        return null;
    }

    /**
     * 反射获取对象指定名称的字段值
     *
     * @param item 目标对象
     * @param name 字段名称
     * @return 字段值，获取失败返回null
     */
    private Object getValueByName(Object item, String name) {
        try {
            Field field = getFieldByName(item, name);
            if (field == null) return null;
            field.setAccessible(true);
            Object obj = field.get(item);
            field.setAccessible(false);
            return obj;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 递归搜索类（含父类）的指定名称字段
     *
     * @param item 目标对象
     * @param name 字段名称
     * @return 字段实例，未找到返回null
     */
    private Field getFieldByName(Object item, String name) {
        Class<?> clazz = item.getClass();
        while (clazz != Object.class) {
            try {
                return clazz.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        return null;
    }
}