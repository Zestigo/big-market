package com.c.config;

import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import com.c.types.annotations.DCCConfiguration;
import com.c.types.annotations.DCCValue;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ReflectionUtils;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.StringReader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.*;

/**
 * DCC (Dynamic Configuration Center) 动态配置核心处理器
 * 核心功能：
 * 1. 扫描并处理带有 @DCCValue 注解的字段，实现配置的自动注入
 * 2. 监听 Nacos 配置变更，实现配置的动态更新（无需重启应用）
 * 3. 提供配置缓存、失败重试、敏感信息脱敏等增强能力
 * 优化点：
 * 1. eligibleBeans 缓存：标记类是否包含 DCC 注解，避免重复扫描非目标类
 * 2. fieldCache 缓存：缓存解析后的字段元数据，避免配置更新时重复反射解析注解
 * 3. configCache 缓存：缓存解析后的配置内容，减少重复解析 YAML/Properties 开销
 *
 * @author cyh
 * @date 2026/02/22
 */
@Slf4j
@Configuration
public class DCCValueBeanFactory implements DestructionAwareBeanPostProcessor {

    // ======================== 常量定义区 ========================
    /** 默认 Nacos 配置分组 */
    private static final String DEFAULT_GROUP = "DEFAULT_GROUP";
    /** Nacos 配置读取超时时间（毫秒） */
    private static final long NACOS_CONFIG_TIMEOUT = 5000L;
    /** Nacos 配置读取失败重试次数 */
    private static final int NACOS_RETRY_TIMES = 3;
    /** 敏感字段关键词集合（用于日志脱敏） */
    private static final Set<String> SENSITIVE_KEYWORDS = new HashSet<>(Arrays.asList("password", "secret", "token",
            "key"));

    /**
     * 配置更新线程池
     * 核心线程数：5，最大线程数：10，空闲线程存活时间：60秒
     * 队列容量：100，线程名称前缀：dcc-update-thread
     * 线程类型：守护线程（不阻塞应用退出）
     */
    private static final Executor CONFIG_EXECUTOR = new ThreadPoolExecutor(5, 10, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(100), r -> {
        Thread thread = new Thread(r, "dcc-update-thread");
        thread.setDaemon(true); // 设置为守护线程，随主线程退出而销毁
        return thread;
    });

    // ======================== 成员变量定义区 ========================
    /** 配置解析结果缓存：key=数据ID_内容哈希值，value=解析后的配置对象+创建时间 */
    private final ConcurrentHashMap<String, CacheEntry> configCache = new ConcurrentHashMap<>();
    /** 监听器注册表：key=类名_Bean名称，value=监听器记录列表（用于销毁时移除监听） */
    private final ConcurrentHashMap<String, List<ListenerRecord>> listenerRegistry = new ConcurrentHashMap<>();
    /** Bean 注解标记缓存：key=目标类，value=是否包含DCC注解（避免重复扫描） */
    private final ConcurrentHashMap<Class<?>, Boolean> eligibleBeans = new ConcurrentHashMap<>();
    /** 字段元数据缓存：key=目标类，value=DCC字段元数据列表（避免重复反射解析） */
    private final ConcurrentHashMap<Class<?>, List<DccFieldMeta>> fieldCache = new ConcurrentHashMap<>();

    /** Nacos 配置服务核心实例（通过构造器注入） */
    private final ConfigService configService;
    /** YAML 解析器（使用SafeConstructor避免安全风险） */
    private final Yaml yamlParser = new Yaml(new SafeConstructor());

    // ======================== 内部数据结构 ========================

    /**
     * 配置缓存条目：存储解析后的配置内容和创建时间
     * 用于缓存YAML/Properties解析结果，减少重复解析开销
     */
    @Getter
    private static class CacheEntry {
        /** 解析后的配置对象（Map/Properties） */
        private final Object parsedContent;
        /** 缓存创建时间戳（毫秒） */
        private final long createTime;

        public CacheEntry(Object parsedContent) {
            this.parsedContent = parsedContent;
            this.createTime = System.currentTimeMillis();
        }

        /**
         * 判断缓存是否过期
         *
         * @return true=过期，false=有效
         */
        public boolean isExpired() {
            // 缓存有效期30分钟，超时后重新解析配置
            return System.currentTimeMillis() - createTime > 1000 * 60 * 30;
        }
    }

    /**
     * 监听器记录：存储监听器相关元数据
     * 用于Bean销毁时移除对应的Nacos配置监听
     */
    @Getter
    private static class ListenerRecord {
        /** Nacos配置数据ID */
        private final String dataId;
        /** Nacos配置分组 */
        private final String group;
        /** 配置变更监听器实例 */
        private final Listener listener;

        public ListenerRecord(String dataId, String group, Listener listener) {
            this.dataId = dataId;
            this.group = group;
            this.listener = listener;
        }
    }

    /**
     * DCC字段元数据：封装@DCCValue注解解析结果
     * 缓存字段注解信息，避免配置更新时重复反射解析
     */
    @Getter
    private static class DccFieldMeta {
        /** 目标字段对象 */
        private final Field field;
        /** 完整配置键（包含类前缀） */
        private final String fullKey;
        /** Nacos配置数据ID */
        private final String dataId;
        /** Nacos配置分组 */
        private final String group;
        /** 字段默认值（注解中配置） */
        private final String defaultValue;

        public DccFieldMeta(Field field, String fullKey, String dataId, String group, String defaultValue) {
            this.field = field;
            this.fullKey = fullKey;
            this.dataId = dataId;
            this.group = group;
            this.defaultValue = defaultValue;
        }
    }

    /**
     * 构造器：注入Nacos配置服务实例
     *
     * @param configService Nacos ConfigService实例
     */
    public DCCValueBeanFactory(ConfigService configService) {
        this.configService = configService;
    }

    // ======================== 核心后置处理逻辑 ========================

    /**
     * Bean初始化后置处理器：核心入口方法
     * 1. 检查Bean是否包含DCC相关注解
     * 2. 解析并缓存字段元数据
     * 3. 初始化字段值
     * 4. 注册配置变更监听器
     *
     * @param bean     初始化后的Bean实例
     * @param beanName Bean名称
     * @return 处理后的Bean实例
     * @throws BeansException Bean处理异常
     */
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        // 获取目标类（解AOP代理）
        Class<?> targetClass = AopUtils.getTargetClass(bean);

        // 优化1：快速失败检查 - 缓存中标记为无需处理的类直接跳过
        if (!eligibleBeans.computeIfAbsent(targetClass, this::hasDCCAnnotations)) {
            return bean;
        }

        // 获取真实的目标对象（解AOP代理）
        Object targetObject = AopProxyUtils.getSingletonTarget(bean);
        if (null == targetObject) {
            targetObject = bean;
        }

        final Object finalTarget = targetObject;

        // 优化2：缓存字段元数据 - 避免同一个类的多个实例重复解析注解
        List<DccFieldMeta> metas = fieldCache.computeIfAbsent(targetClass, clazz -> {
            List<DccFieldMeta> list = new ArrayList<>();

            // 获取类级别配置（前缀、数据ID、分组）
            DCCConfiguration classConfig = clazz.getAnnotation(DCCConfiguration.class);
            String classPrefix = (classConfig != null) ? classConfig.prefix() : "";
            String classDataId = (classConfig != null) ? classConfig.dataId() : "";
            String classGroup = (classConfig != null) ? classConfig.group() : DEFAULT_GROUP;

            // 遍历所有字段，解析@DCCValue注解
            ReflectionUtils.doWithFields(clazz, field -> {
                DCCValue dccValue = field.getAnnotation(DCCValue.class);
                if (dccValue != null) {
                    // 解析注解值表达式：格式为 "配置键:默认值"
                    String valueExpr = dccValue
                            .value()
                            .trim();
                    String[] splits = valueExpr.split(":", 2);
                    String rawKey = splits[0].trim();
                    String defVal = splits.length == 2 ? splits[1].trim() : null;

                    // 构建完整配置键（类前缀 + 字段键）
                    String fullKey = buildFullKey(classPrefix, rawKey);
                    // 优先级：字段注解 > 类注解 > 默认值
                    String dataId = StringUtils.isNotBlank(dccValue.dataId()) ? dccValue.dataId() : classDataId;
                    String group = StringUtils.isNotBlank(dccValue.group()) ? dccValue.group() : classGroup;

                    // 封装字段元数据并加入列表
                    list.add(new DccFieldMeta(field, fullKey, dataId, group, defVal));
                }
            });
            return list;
        });

        // 3. 执行字段值注入与配置监听注册
        for (DccFieldMeta meta : metas) {
            // 初始化字段值（从Nacos读取配置并注入）
            initFieldValue(finalTarget, beanName, meta.getField(), meta.getFullKey(), meta.getDataId(),
                    meta.getGroup(), meta.getDefaultValue());
            // 注册配置变更监听器（配置更新时自动刷新字段值）
            registerConfigListener(finalTarget, beanName, meta.getField(), meta.getFullKey(), meta.getDataId(),
                    meta.getGroup());
        }

        return bean;
    }

    /**
     * Bean销毁前置处理器：清理资源
     * 移除当前Bean注册的所有Nacos配置监听器，避免内存泄漏
     *
     * @param bean     待销毁的Bean实例
     * @param beanName Bean名称
     * @throws BeansException Bean处理异常
     */
    @Override
    public void postProcessBeforeDestruction(Object bean, String beanName) throws BeansException {
        // 构建监听器注册表Key：类名_Bean名称
        String beanKey = AopUtils
                .getTargetClass(bean)
                .getName() + "_" + beanName;
        // 移除并获取当前Bean的所有监听器记录
        List<ListenerRecord> records = listenerRegistry.remove(beanKey);

        if (records != null) {
            // 遍历移除所有监听器
            for (ListenerRecord r : records) {
                try {
                    configService.removeListener(r.getDataId(), r.getGroup(), r.getListener());
                    log.debug("[DCC] Remove listener success. bean:{} dataId:{}", beanName, r.getDataId());
                } catch (Exception e) { // 捕获通用Exception，覆盖所有运行时异常
                    // 记录移除监听器失败的异常，不中断后续处理
                    log.error("[DCC] Remove listener failed. bean:{} dataId:{} group:{}", beanName, r.getDataId(),
                            r.getGroup(), e);
                }
            }
        }
    }

    /**
     * 判断Bean是否需要销毁处理
     * 仅当Bean包含DCC注解时才需要执行销毁逻辑
     *
     * @param bean Bean实例
     * @return true=需要销毁处理，false=不需要
     */
    @Override
    public boolean requiresDestruction(Object bean) {
        // 复用eligibleBeans缓存，避免重复扫描
        return eligibleBeans.getOrDefault(AopUtils.getTargetClass(bean), false);
    }

    // ======================== 私有逻辑实现 ========================

    /**
     * 初始化字段值：从Nacos读取配置并注入到目标字段
     *
     * @param target       目标对象
     * @param beanName     Bean名称
     * @param field        目标字段
     * @param fullKey      完整配置键
     * @param dataId       Nacos配置数据ID
     * @param group        Nacos配置分组
     * @param defaultValue 默认值
     */
    private void initFieldValue(Object target, String beanName, Field field, String fullKey, String dataId,
                                String group, String defaultValue) {
        // 从Nacos读取配置内容（带重试机制）
        String content = getConfigWithRetry(dataId, group);
        // 智能提取配置值（支持YAML/Properties格式）
        String extractedValue = smartExtract(target, content, fullKey, dataId);
        // 确定最终值：配置值 > 默认值
        String finalValue = StringUtils.isNotBlank(extractedValue) ? extractedValue : defaultValue;

        if (StringUtils.isNotBlank(finalValue)) {
            // 反射更新字段值
            updateFieldValue(target, field, finalValue);
            // 日志输出（敏感字段自动脱敏）
            log.debug("[DCC] Initial injection success. bean:{} field:{} value:{}", beanName, field.getName(),
                    mask(field.getName(), finalValue));
        }
    }

    /**
     * 注册Nacos配置变更监听器
     * 配置更新时自动提取新值并更新目标字段
     *
     * @param target   目标对象
     * @param beanName Bean名称
     * @param field    目标字段
     * @param fullKey  完整配置键
     * @param dataId   Nacos配置数据ID
     * @param group    Nacos配置分组
     */
    private void registerConfigListener(Object target, String beanName, Field field, String fullKey, String dataId,
                                        String group) {
        try {
            // 创建配置变更监听器
            Listener listener = new Listener() {
                @Override
                public void receiveConfigInfo(String configInfo) {
                    // 配置变更时提取新值
                    String newValue = smartExtract(target, configInfo, fullKey, dataId);
                    if (StringUtils.isNotBlank(newValue)) {
                        // 反射更新字段值
                        updateFieldValue(target, field, newValue);
                        // 日志输出（敏感字段自动脱敏）
                        log.info("[DCC] Dynamic update success. bean:{} field:{} value:{}", beanName, field.getName()
                                , mask(field.getName(), newValue));
                    }
                }

                @Override
                public Executor getExecutor() {
                    // 使用专用线程池处理配置更新
                    return CONFIG_EXECUTOR;
                }
            };

            // 注册监听器到Nacos
            configService.addListener(dataId, group, listener);

            // 记录监听器信息（用于销毁时移除）
            String beanKey = AopUtils
                    .getTargetClass(target)
                    .getName() + "_" + beanName;
            listenerRegistry
                    .computeIfAbsent(beanKey, k -> new CopyOnWriteArrayList<>())
                    .add(new ListenerRecord(dataId, group, listener));

            log.debug("[DCC] Register listener success. bean:{} dataId:{}", beanName, dataId);
        } catch (NacosException e) {
            log.error("[DCC] Failed to register listener. dataId:{} group:{}", dataId, group, e);
        }
    }

    /**
     * 智能提取配置值：支持YAML/Properties/纯文本格式
     * 内置缓存机制，避免重复解析配置内容
     *
     * @param target  目标对象（仅用于日志，无实际业务作用）
     * @param content 原始配置内容
     * @param fullKey 完整配置键
     * @param dataId  Nacos配置数据ID（用于判断配置格式）
     * @return 提取到的配置值，无则返回null
     */
    @SuppressWarnings("unchecked")
    private String smartExtract(Object target, String content, String fullKey, String dataId) {
        if (StringUtils.isBlank(content)) return null;
        // 如果dataId等于配置键，直接返回完整内容（适用于纯文本配置）
        if (dataId.equals(fullKey)) return content;

        // 构建缓存Key：数据ID + 内容哈希值（内容不变则缓存有效）
        String cacheKey = dataId + "_" + content.hashCode();
        CacheEntry entry = configCache.get(cacheKey);

        // YAML格式配置解析
        if (dataId.endsWith(".yaml") || dataId.endsWith(".yml")) {
            // 缓存不存在或过期时重新解析
            if (entry == null || entry.isExpired()) {
                entry = new CacheEntry(yamlParser.load(content));
                configCache.put(cacheKey, entry);
            }
            // 从YAML解析结果中提取指定键的值
            return extractYamlValue((Map<String, Object>) entry.getParsedContent(), fullKey);
        }

        // Properties格式配置解析
        if (dataId.endsWith(".properties")) {
            // 缓存不存在或过期时重新解析
            if (entry == null || entry.isExpired()) {
                Properties props = new Properties();
                try {
                    props.load(new StringReader(content));
                } catch (Exception ignored) {
                    log.warn("[DCC] Parse properties failed. dataId:{}", dataId);
                }
                entry = new CacheEntry(props);
                configCache.put(cacheKey, entry);
            }
            // 从Properties中提取指定键的值
            return ((Properties) entry.getParsedContent()).getProperty(fullKey);
        }

        // 其他格式直接返回完整内容
        return content;
    }

    /**
     * 反射更新字段值
     * 1. 处理非公有字段的访问权限
     * 2. 自动类型转换（String -> 目标类型）
     * 3. 加锁保证线程安全
     *
     * @param target 目标对象
     * @param field  目标字段
     * @param value  待设置的字符串值
     */
    private void updateFieldValue(Object target, Field field, String value) {
        // 同步锁：避免多线程同时更新字段值
        synchronized (target) {
            try {
                // 仅对非公有字段开启访问权限（最小权限原则）
                if (!Modifier.isPublic(field.getModifiers())) {
                    field.setAccessible(true);
                }
                // 类型转换：将字符串值转换为字段类型
                Object convertedValue = convertType(field.getType(), value);
                // 设置字段值
                field.set(target, convertedValue);
            } catch (Exception e) {
                log.error("[DCC] Reflection update failed. field:{} value:{}", field.getName(), value, e);
            }
        }
    }

    /**
     * 类型转换：将字符串值转换为目标类型
     * 支持的类型：String、int/Integer、long/Long、boolean/Boolean、double/Double
     *
     * @param type  目标类型
     * @param value 字符串值
     * @return 转换后的对象
     * @throws NumberFormatException 数字格式异常
     */
    private Object convertType(Class<?> type, String value) {
        if (type == String.class) return value;
        if (type == Integer.class || type == int.class) return Integer.parseInt(value);
        if (type == Long.class || type == long.class) return Long.parseLong(value);
        if (type == Boolean.class || type == boolean.class) return "true".equalsIgnoreCase(value) || "1".equals(value);
        if (type == Double.class || type == double.class) return Double.parseDouble(value);
        // 不支持的类型返回原始字符串
        return value;
    }

    /**
     * 从YAML解析结果中提取指定键的值
     * 支持多级键（例如：spring.datasource.url）
     *
     * @param map YAML解析后的Map对象
     * @param key 配置键（支持多级，点分隔）
     * @return 提取到的值（字符串形式），无则返回null
     */
    @SuppressWarnings("unchecked")
    private String extractYamlValue(Map<String, Object> map, String key) {
        if (map == null) return null;
        // 拆分多级键
        String[] keys = key.split("\\.");
        Object value = map;

        // 逐级查找值
        for (String k : keys) {
            if (value instanceof Map) {
                value = ((Map<String, Object>) value).get(k);
            } else {
                // 中间层级不是Map，说明键不存在
                return null;
            }
        }

        // 转换为字符串返回（null则返回null）
        return value != null ? value.toString() : null;
    }

    /**
     * 带重试机制的Nacos配置读取
     * 读取失败时自动重试，最多重试NACOS_RETRY_TIMES次
     *
     * @param dataId 配置数据ID
     * @param group  配置分组
     * @return 配置内容，全部重试失败则返回null
     */
    private String getConfigWithRetry(String dataId, String group) {
        for (int i = 0; i < NACOS_RETRY_TIMES; i++) {
            try {
                // 从Nacos读取配置
                return configService.getConfig(dataId, group, NACOS_CONFIG_TIMEOUT);
            } catch (Exception e) {
                // 非最后一次重试时记录警告日志
                log.warn("[DCC] Nacos read failed, retrying {}/{} dataId:{}", i + 1, NACOS_RETRY_TIMES, dataId);
            }
        }
        // 全部重试失败返回null
        return null;
    }

    /**
     * 检查类是否包含DCC相关注解
     * 递归检查当前类及其所有父类（直到Object.class）
     *
     * @param clazz 待检查的类
     * @return true=包含DCC注解，false=不包含
     */
    private boolean hasDCCAnnotations(Class<?> clazz) {
        Class<?> current = clazz;
        // 递归检查当前类和所有父类
        while (current != null && current != Object.class) {
            // 检查类级别@DCCConfiguration注解
            if (current.isAnnotationPresent(DCCConfiguration.class)) {
                return true;
            }
            // 检查字段级别@DCCValue注解
            for (Field f : current.getDeclaredFields()) {
                if (f.isAnnotationPresent(DCCValue.class)) {
                    return true;
                }
            }
            // 继续检查父类
            current = current.getSuperclass();
        }
        return false;
    }

    /**
     * 构建完整配置键
     * 处理前缀末尾的点号，避免重复（例如：prefix. + key = prefix.key）
     *
     * @param prefix 类级别的前缀
     * @param key    字段级别的键
     * @return 完整的配置键
     */
    private String buildFullKey(String prefix, String key) {
        if (StringUtils.isBlank(prefix)) {
            return key;
        }
        // 前缀末尾有点号则直接拼接，否则添加点号
        return prefix.endsWith(".") ? prefix + key : prefix + "." + key;
    }

    /**
     * 敏感信息脱敏
     * 包含敏感关键词的字段值替换为******
     *
     * @param fieldName 字段名称
     * @param value     原始值
     * @return 脱敏后的值
     */
    private String mask(String fieldName, String value) {
        if (StringUtils.isBlank(value)) {
            return value;
        }
        // 检查字段名是否包含敏感关键词（不区分大小写）
        boolean sensitive = SENSITIVE_KEYWORDS
                .stream()
                .anyMatch(keyword -> fieldName
                        .toLowerCase()
                        .contains(keyword));
        // 敏感字段返回******，非敏感字段返回原值
        return sensitive ? "******" : value;
    }
}