package com.c.config;

import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import com.c.types.annotations.DCCConfiguration;
import com.c.types.annotations.DCCValue;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ReflectionUtils;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.StringReader;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * DCC (Dynamic Configuration Center) 动态配置处理器核心类
 * 核心功能：
 * 1. 基于 Spring DestructionAwareBeanPostProcessor 扩展，实现 Nacos 配置自动注入
 * 2. 支持 @DCCConfiguration（类级）和 @DCCValue（字段级）注解解析
 * 3. 兼容 Text/Properties/YAML 三种配置格式的智能解析
 * 4. 实现配置热更新，支持字段值动态刷新
 * 5. 提供配置值脱敏、类型自动转换、缓存优化、监听反注册等增强能力
 * 核心特性：
 * - 线程安全：所有关键操作加锁，避免并发更新异常
 * - 内存安全：Bean销毁时清理缓存+反注册监听，无内存泄漏
 * - 容错性：配置读取失败自动重试，异常不扩散
 * - 性能优化：配置解析缓存（带过期），独立线程池处理更新
 * - 安全防护：安全YAML解析、敏感字段脱敏、线程池限流
 *
 * @author cyh
 * @date 2026/02/21
 */
@Slf4j
@Configuration
public class DCCValueBeanFactory implements DestructionAwareBeanPostProcessor {
    // ======================== 常量定义区 ========================
    /** Nacos 默认分组名 */
    private static final String DEFAULT_GROUP = "DEFAULT_GROUP";

    /** Nacos 配置读取超时时间（毫秒） */
    private static final long NACOS_CONFIG_TIMEOUT = 5000L;

    /** Nacos 配置读取重试次数 */
    private static final int NACOS_RETRY_TIMES = 3;

    /** Nacos 配置读取重试间隔（毫秒） */
    private static final long NACOS_RETRY_INTERVAL = 1000L;

    /** YAML 配置文件后缀（.yaml） */
    private static final String YAML_SUFFIX_YAML = ".yaml";

    /** YAML 配置文件后缀（.yml） */
    private static final String YAML_SUFFIX_YML = ".yml";

    /** Properties 配置文件后缀 */
    private static final String PROPERTIES_SUFFIX = ".properties";

    /** 敏感字段关键词（匹配这些关键词的字段会脱敏） */
    private static final Set<String> SENSITIVE_FIELD_KEYWORDS = new HashSet<>(Arrays.asList("password", "pwd",
            "secret", "key", "token", "accessKey", "secretKey", "credential"));

    /** 配置缓存过期时间（分钟） */
    private static final long CACHE_EXPIRE_MINUTES = 30;

    /** 配置缓存最大容量（避免内存膨胀） */
    private static final int CACHE_MAX_SIZE = 1000;

    // ======================== 核心成员变量（线程安全设计）========================
    /** 配置更新线程池（生产级配置） */
    private static final Executor CONFIG_UPDATE_EXECUTOR = new ThreadPoolExecutor(5, 10, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(100), r -> {
        Thread thread = new Thread(r, "dcc-config-update-thread-" + System.currentTimeMillis());
        thread.setDaemon(true); // 设置为守护线程，不阻塞应用关闭
        return thread;
    }, (r, executor) -> log.error("[DCC] 配置更新线程池已满，任务被拒绝 - Task: {}", r.toString()));

    /** 配置解析缓存（带过期+容量限制） */
    private final ConcurrentHashMap<String, CacheEntry> configContentCache = new ConcurrentHashMap<>();

    /** Nacos监听器注册表（用于Bean销毁时反注册） */
    private final ConcurrentHashMap<String, List<ListenerRecord>> listenerRegistry = new ConcurrentHashMap<>();

    /** Nacos 配置服务核心对象（由 Spring 注入） */
    private final ConfigService configService;

    /** YAML 解析器（安全模式） */
    private final Yaml yamlParser = new Yaml(new SafeConstructor());

    /** 缓存清理计数器（避免频繁清理） */
    private final AtomicLong cacheCleanCount = new AtomicLong(0);

    // ======================== 内部类定义 =========================

    /**
     * 缓存条目（包含解析结果和创建时间）
     */
    @Getter
    private static class CacheEntry {
        private final Object parsedContent; // 解析后的配置内容
        private final long createTime;      // 缓存创建时间戳

        public CacheEntry(Object parsedContent) {
            this.parsedContent = parsedContent;
            this.createTime = System.currentTimeMillis();
        }

        /**
         * 判断缓存是否过期
         *
         * @return true-过期，false-未过期
         */
        public boolean isExpired() {
            return System.currentTimeMillis() - createTime > DateUtils.MILLIS_PER_MINUTE * CACHE_EXPIRE_MINUTES;
        }
    }

    /**
     * 监听器记录（用于反注册）
     */
    @Getter
    private static class ListenerRecord {
        private final String dataId;  // Nacos配置DataId
        private final String group;   // Nacos配置分组
        private final Listener listener; // Nacos配置监听器

        public ListenerRecord(String dataId, String group, Listener listener) {
            this.dataId = dataId;
            this.group = group;
            this.listener = listener;
        }
    }

    // ======================== 构造方法 =========================

    /**
     * 构造方法，初始化Nacos配置服务并启动缓存清理定时任务
     *
     * @param configService Nacos配置服务实例（Spring自动注入）
     */
    public DCCValueBeanFactory(ConfigService configService) {
        this.configService = configService;
        scheduleCacheCleanup(); // 启动定时缓存清理任务
    }

    // ======================== 核心接口实现（完整逻辑）========================

    /**
     * Bean初始化完成后处理：扫描DCC注解并注入配置值，注册配置监听器
     *
     * @param bean     待处理的Bean实例
     * @param beanName Bean在Spring容器中的名称
     * @return 处理后的Bean实例（原样返回，仅修改字段值）
     * @throws BeansException Bean处理过程中发生的异常
     */
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Class<?> beanClass = bean.getClass();
        // 快速判断：如果Bean没有DCC相关注解，直接返回，提升性能
        if (!hasDCCAnnotations(beanClass)) {
            return bean;
        }

        // 获取类级别的DCC配置注解（可选）
        DCCConfiguration classConfig = beanClass.getAnnotation(DCCConfiguration.class);
        // 解析类级别的前缀、DataId、Group（为空则使用默认值）
        String classPrefix = (classConfig != null && StringUtils.isNotBlank(classConfig.prefix())) ?
                classConfig.prefix() : "";
        String classDataId = (classConfig != null && StringUtils.isNotBlank(classConfig.dataId())) ?
                classConfig.dataId() : "";
        String classGroup = (classConfig != null && StringUtils.isNotBlank(classConfig.group())) ?
                classConfig.group() : DEFAULT_GROUP;

        // 遍历所有字段，处理@DCCValue注解
        ReflectionUtils.doWithFields(beanClass, field -> {
            DCCValue dccValue = field.getAnnotation(DCCValue.class);
            if (dccValue == null) {
                return; // 无DCCValue注解，跳过
            }

            // 解析注解值：格式为 "配置键: 默认值"
            String valueExpr = dccValue
                    .value()
                    .trim();
            if (StringUtils.isBlank(valueExpr)) {
                log.warn("[DCC] 注解值为空 - BeanName: {}, Field: {}", beanName, field.getName());
                return;
            }

            // 分割配置键和默认值（支持默认值包含冒号）
            String[] splits = valueExpr.split(":", 2);
            String rawKey = splits[0].trim();
            String defaultValue = splits.length == 2 ? splits[1].trim() : null;

            // 构建完整的配置键（拼接类前缀）
            String fullKey = buildFullKey(classPrefix, rawKey);
            // 解析最终的DataId（字段注解优先，其次类注解，最后使用原始键）
            String dataId = resolveDataId(dccValue, classDataId, rawKey);
            // 解析最终的Group（字段注解优先，其次类注解，最后使用默认分组）
            String group = resolveGroup(dccValue, classGroup);

            try {
                // 初始化字段值（修复：内部捕获所有异常，不再抛出）
                initFieldValue(bean, beanName, field, fullKey, dataId, group, defaultValue);
                // 注册配置监听器，实现热更新
                registerConfigListener(bean, beanName, field, fullKey, dataId, group);
            } catch (Exception e) { // 统一捕获所有异常，避免影响其他字段处理
                log.error("[DCC] 初始化字段失败 - BeanName: {}, Field: {}, DataId: {}, Group: {}", beanName, field.getName()
                        , dataId, group, e);
            }
        });

        return bean;
    }

    /**
     * Bean销毁前处理：反注册Nacos监听器，清理缓存，防止内存泄漏
     *
     * @param bean     待销毁的Bean实例
     * @param beanName Bean在Spring容器中的名称
     * @throws BeansException Bean处理过程中发生的异常
     */
    @Override
    public void postProcessBeforeDestruction(Object bean, String beanName) throws BeansException {
        String beanKey = buildBeanKey(bean, beanName);

        // 第一步：反注册当前Bean关联的所有Nacos监听器
        List<ListenerRecord> listenerRecords = listenerRegistry.remove(beanKey);
        if (listenerRecords != null && !listenerRecords.isEmpty()) {
            for (ListenerRecord record : listenerRecords) {
                try {
                    // 注意：removeListener方法本身不抛出NacosException，捕获通用Exception保证鲁棒性
                    configService.removeListener(record.getDataId(), record.getGroup(), record.getListener());
                    log.debug("[DCC] 反注册Nacos监听成功 - BeanName: {}, DataId: {}, Group: {}", beanName,
                            record.getDataId(), record.getGroup());
                } catch (Exception e) {
                    log.error("[DCC] 反注册Nacos监听失败 - BeanName: {}, DataId: {}, Group: {}", beanName,
                            record.getDataId(), record.getGroup(), e);
                }
            }
        }

        // 第二步：清理当前Bean关联的配置解析缓存
        String cacheKeyPrefix = buildCacheKeyPrefix(bean);
        configContentCache
                .keySet()
                .removeIf(key -> key.startsWith(cacheKeyPrefix));
        log.debug("[DCC] 清理Bean缓存成功 - BeanName: {}, 清理前缀: {}", beanName, cacheKeyPrefix);

        // 第三步：每处理100个Bean，触发一次全局过期缓存清理（避免频繁清理）
        if (cacheCleanCount.incrementAndGet() % 100 == 0) {
            cleanExpiredCache();
            cacheCleanCount.set(0); // 重置计数器
        }
    }

    /**
     * 判断Bean是否需要销毁处理（是否包含DCC相关注解）
     *
     * @param bean 待判断的Bean实例
     * @return true-需要销毁处理，false-不需要
     */
    @Override
    public boolean requiresDestruction(Object bean) {
        return hasDCCAnnotations(bean.getClass());
    }

    /**
     * Bean初始化前处理：无操作，直接返回原Bean
     *
     * @param bean     待处理的Bean实例
     * @param beanName Bean在Spring容器中的名称
     * @return 原Bean实例
     * @throws BeansException Bean处理过程中发生的异常
     */
    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        return bean;
    }

    // ======================== 私有工具方法（修复核心编译错误）========================

    /**
     * 判断类是否包含DCC相关注解（类级@DCCConfiguration 或 字段级@DCCValue）
     *
     * @param beanClass 待判断的类
     * @return true-包含，false-不包含
     */
    private boolean hasDCCAnnotations(Class<?> beanClass) {
        // 优先判断类注解，提升性能
        if (beanClass.isAnnotationPresent(DCCConfiguration.class)) {
            return true;
        }
        // 遍历字段判断是否有字段注解
        for (Field field : beanClass.getDeclaredFields()) {
            if (field.isAnnotationPresent(DCCValue.class)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 构建完整的配置键（拼接类前缀）
     *
     * @param classPrefix 类级别的配置前缀
     * @param rawKey      字段级别的原始配置键
     * @return 拼接后的完整配置键
     */
    private String buildFullKey(String classPrefix, String rawKey) {
        if (StringUtils.isBlank(classPrefix)) {
            return rawKey;
        }
        // 处理前缀末尾的点号，避免重复
        String prefix = classPrefix.endsWith(".") ? classPrefix : classPrefix + ".";
        // 处理原始键开头的点号，避免多余
        return rawKey.startsWith(".") ? prefix + rawKey.substring(1) : prefix + rawKey;
    }

    /**
     * 解析最终的DataId（优先级：字段注解 > 类注解 > 原始键）
     *
     * @param dccValue    字段级@DCCValue注解
     * @param classDataId 类级@DCCConfiguration的DataId
     * @param rawKey      字段原始键
     * @return 最终使用的DataId
     */
    private String resolveDataId(DCCValue dccValue, String classDataId, String rawKey) {
        if (StringUtils.isNotBlank(dccValue.dataId())) {
            return dccValue
                    .dataId()
                    .trim();
        }
        return StringUtils.isNotBlank(classDataId) ? classDataId.trim() : rawKey.trim();
    }

    /**
     * 解析最终的Group（优先级：字段注解 > 类注解 > 默认分组）
     *
     * @param dccValue   字段级@DCCValue注解
     * @param classGroup 类级@DCCConfiguration的Group
     * @return 最终使用的Group
     */
    private String resolveGroup(DCCValue dccValue, String classGroup) {
        return StringUtils.isNotBlank(dccValue.group()) ? dccValue
                .group()
                .trim() : classGroup.trim();
    }

    /**
     * 初始化字段值（从Nacos读取配置，解析后注入字段）
     * 修复点：移除throws NacosException声明，内部捕获所有Nacos异常
     *
     * @param bean         Bean实例
     * @param beanName     Bean名称
     * @param field        待初始化的字段
     * @param fullKey      完整配置键
     * @param dataId       Nacos配置DataId
     * @param group        Nacos配置Group
     * @param defaultValue 默认值（配置读取失败时使用）
     */
    private void initFieldValue(Object bean, String beanName, Field field, String fullKey, String dataId,
                                String group, String defaultValue) {
        String content = null;
        try {
            // 调用带重试的配置读取方法（内部处理NacosException）
            content = getConfigWithRetry(dataId, group);
        } catch (Exception e) {
            log.error("[DCC] 读取Nacos配置失败 - BeanName: {}, DataId: {}, Group: {}", beanName, dataId, group, e);
            // 读取失败时使用默认值（如果有）
            if (StringUtils.isNotBlank(defaultValue)) {
                updateFieldValue(bean, beanName, field, defaultValue, dataId, fullKey);
                log.warn("[DCC] 使用默认值初始化字段 - BeanName: {}, Field: {}, DefaultValue: {}", beanName, field.getName(),
                        maskSensitiveValue(field.getName(), defaultValue));
            }
            return;
        }

        // 智能解析配置内容，提取目标值
        String extractedValue = smartExtract(bean, content, fullKey, dataId);
        // 最终值：解析值优先，其次默认值
        String finalValue = StringUtils.isNotBlank(extractedValue) ? extractedValue : defaultValue;

        if (StringUtils.isNotBlank(finalValue)) {
            // 注入字段值
            updateFieldValue(bean, beanName, field, finalValue, dataId, fullKey);
            log.debug("[DCC] 初始化字段成功 - BeanName: {}, Field: {}, Value: {}", beanName, field.getName(),
                    maskSensitiveValue(field.getName(), finalValue));
        } else {
            log.warn("[DCC] 未获取到配置值 - BeanName: {}, DataId: {}, Group: {}, Key: {}, 默认值: {}", beanName, dataId, group
                    , fullKey, defaultValue);
        }
    }

    /**
     * 注册Nacos配置监听器，实现配置热更新
     * 修复点：捕获NacosException，避免编译错误，保证监听器注册失败不影响主流程
     *
     * @param bean     Bean实例
     * @param beanName Bean名称
     * @param field    待监听的字段
     * @param fullKey  完整配置键
     * @param dataId   Nacos配置DataId
     * @param group    Nacos配置Group
     */
    private void registerConfigListener(Object bean, String beanName, Field field, String fullKey, String dataId,
                                        String group) {
        try {
            Listener listener = new Listener() {
                @Override
                public void receiveConfigInfo(String configInfo) {
                    try {
                        // 配置变更时，重新解析值
                        String newValue = smartExtract(bean, configInfo, fullKey, dataId);
                        if (StringUtils.isBlank(newValue)) {
                            log.warn("[DCC] 配置更新后值为空 - BeanName: {}, DataId: {}, Group: {}, Key: {}", beanName,
                                    dataId, group, fullKey);
                            return;
                        }
                        // 热更新字段值
                        updateFieldValue(bean, beanName, field, newValue, dataId, fullKey);
                        log.info("[DCC] 字段热更新成功 - BeanName: {}, Field: {}, NewValue: {}", beanName, field.getName(),
                                maskSensitiveValue(field.getName(), newValue));
                    } catch (Exception e) {
                        log.error("[DCC] 字段热更新失败 - BeanName: {}, DataId: {}, Group: {}, Key: {}", beanName, dataId,
                                group, fullKey, e);
                    }
                }

                @Override
                public Executor getExecutor() {
                    // 使用独立线程池处理配置更新，避免阻塞Nacos客户端线程
                    return CONFIG_UPDATE_EXECUTOR;
                }
            };

            // 注册监听器到Nacos
            configService.addListener(dataId, group, listener);
            log.debug("[DCC] 注册Nacos监听成功 - BeanName: {}, DataId: {}, Group: {}, Key: {}", beanName, dataId, group,
                    fullKey);

            // 记录监听器，用于Bean销毁时反注册
            String beanKey = buildBeanKey(bean, beanName);
            listenerRegistry
                    .computeIfAbsent(beanKey, k -> new CopyOnWriteArrayList<>())
                    .add(new ListenerRecord(dataId, group, listener));
        } catch (NacosException e) {
            log.error("[DCC] 注册Nacos监听失败 - BeanName: {}, DataId: {}, Group: {}", beanName, dataId, group, e);
        }
    }

    /**
     * 智能解析配置内容，根据DataId后缀或内容格式自动识别类型（YAML/Properties/Text）
     *
     * @param bean    Bean实例（用于构建缓存键）
     * @param content 原始配置内容
     * @param fullKey 完整配置键
     * @param dataId  Nacos配置DataId
     * @return 解析后的目标值
     */
    private String smartExtract(Object bean, String content, String fullKey, String dataId) {
        if (StringUtils.isBlank(content)) {
            return null;
        }

        // 构建缓存键（避免重复解析相同配置）
        String cacheKey = buildCacheKey(bean, dataId, content);
        CacheEntry cacheEntry = configContentCache.get(cacheKey);

        // 特殊场景：DataId和配置键相同，直接返回完整内容（适用于纯文本配置）
        if (dataId.equals(fullKey)) {
            return content;
        }

        // 场景1：YAML格式配置（后缀为.yaml/.yml 或内容符合YAML特征）
        if (dataId.endsWith(YAML_SUFFIX_YAML) || dataId.endsWith(YAML_SUFFIX_YML) || isYamlContent(content)) {
            // 缓存未命中或过期，重新解析
            if (cacheEntry == null || cacheEntry.isExpired()) {
                Object parsedContent = yamlParser.load(content);
                cacheEntry = new CacheEntry(parsedContent);
                putToCache(cacheKey, cacheEntry); // 放入缓存
            }
            // 从YAML解析结果中提取目标值
            return extractYaml((Map<String, Object>) cacheEntry.getParsedContent(), fullKey);
        }

        // 场景2：Properties格式配置（后缀为.properties 或内容包含等号）
        if (dataId.endsWith(PROPERTIES_SUFFIX) || content.contains("=")) {
            if (cacheEntry == null || cacheEntry.isExpired()) {
                Properties props = new Properties();
                try {
                    props.load(new StringReader(content)); // 解析Properties内容
                    cacheEntry = new CacheEntry(props);
                    putToCache(cacheKey, cacheEntry); // 放入缓存
                } catch (Exception e) {
                    log.error("[DCC] 解析Properties配置失败 - DataId: {}", dataId, e);
                    return null;
                }
            }
            // 从Properties中提取目标值
            return ((Properties) cacheEntry.getParsedContent()).getProperty(fullKey);
        }

        // 场景3：纯文本格式，直接返回内容
        return content;
    }

    /**
     * 判断内容是否为YAML格式（用于无后缀的DataId场景）
     *
     * @param content 配置内容
     * @return true-YAML格式，false-非YAML格式
     */
    private boolean isYamlContent(String content) {
        // YAML特征：包含换行、冒号+空格，不包含等号，且首行符合YAML键值对特征 或 包含列表符号
        return content.contains("\n") && content.contains(": ") && !content.contains("=") && (content.matches("^\\s" + "*\\w+:\\s+.+") || content.contains("- "));
    }

    /**
     * 从YAML解析结果中提取指定键的值（支持多级键，如a.b.c）
     *
     * @param yamlMap YAML解析后的Map
     * @param fullKey 完整配置键（支持多级）
     * @return 提取的值（字符串形式）
     */
    @SuppressWarnings("unchecked")
    private String extractYaml(Map<String, Object> yamlMap, String fullKey) {
        if (yamlMap == null || StringUtils.isBlank(fullKey)) {
            return null;
        }

        // 拆分多级键（按点号分割）
        String[] keys = fullKey.split("\\.");
        Object value = yamlMap;

        try {
            for (String key : keys) {
                if (value instanceof Map) {
                    value = ((Map<String, Object>) value).get(key);
                } else {
                    return null; // 中间层级不是Map，提取失败
                }
                if (value == null) {
                    break; // 键不存在，提前退出
                }
            }
            return value != null ? value.toString() : null;
        } catch (Exception e) {
            log.error("[DCC] 提取YAML配置失败 - Key: {}", fullKey, e);
            return null;
        }
    }

    /**
     * 反射更新字段值（线程安全：加锁Bean实例，避免并发更新）
     *
     * @param bean     Bean实例
     * @param beanName Bean名称
     * @param field    待更新的字段
     * @param value    原始字符串值
     * @param dataId   Nacos配置DataId
     * @param fullKey  完整配置键
     */
    private void updateFieldValue(Object bean, String beanName, Field field, String value, String dataId,
                                  String fullKey) {
        synchronized (bean) { // 锁定Bean实例，保证字段更新线程安全
            try {
                field.setAccessible(true); // 突破私有字段访问限制
                // 类型转换：将字符串值转换为字段对应的类型
                Object convertedValue = convertType(field.getType(), field.getGenericType(), value, beanName, dataId,
                        fullKey);
                field.set(bean, convertedValue); // 注入值
            } catch (IllegalAccessException e) {
                log.error("[DCC] 反射更新字段失败 - BeanName: {}, Field: {}, Value: {}", beanName, field.getName(),
                        maskSensitiveValue(field.getName(), value), e);
            } finally {
                field.setAccessible(false); // 恢复字段访问权限，保证安全性
            }
        }
    }

    /**
     * 类型转换：将字符串值转换为目标类型（支持基本类型、集合、数组）
     *
     * @param type        目标类型（Class）
     * @param genericType 目标泛型类型（Type）
     * @param value       原始字符串值
     * @param beanName    Bean名称
     * @param dataId      Nacos配置DataId
     * @param fullKey     完整配置键
     * @return 转换后的值（转换失败返回对应类型的默认值）
     */
    private Object convertType(Class<?> type, Type genericType, String value, String beanName, String dataId,
                               String fullKey) {
        if (StringUtils.isBlank(value)) {
            return getDefaultValueForType(type); // 值为空，返回类型默认值
        }

        try {
            // 支持的类型：字符串、基本类型、数组、List、Set
            if (type == String.class) {
                return value;
            } else if (type == Integer.class || type == int.class) {
                return Integer.parseInt(value);
            } else if (type == Long.class || type == long.class) {
                return Long.parseLong(value);
            } else if (type == Boolean.class || type == boolean.class) {
                return parseBoolean(value); // 增强布尔值解析（支持true/1/yes）
            } else if (type == Double.class || type == double.class) {
                return Double.parseDouble(value);
            } else if (type == Float.class || type == float.class) {
                return Float.parseFloat(value);
            } else if (type == Short.class || type == short.class) {
                return Short.parseShort(value);
            } else if (type == Byte.class || type == byte.class) {
                return Byte.parseByte(value);
            } else if (type.isArray() && type.getComponentType() == String.class) {
                return value.split(","); // 字符串数组（逗号分隔）
            } else if (List.class.isAssignableFrom(type)) {
                return new ArrayList<>(Arrays.asList(value.split(","))); // List（逗号分隔）
            } else if (Set.class.isAssignableFrom(type)) {
                return new HashSet<>(Arrays.asList(value.split(","))); // Set（逗号分隔）
            } else {
                log.warn("[DCC] 不支持的字段类型 - BeanName: {}, DataId: {}, Key: {}, Type: {}", beanName, dataId, fullKey,
                        type.getName());
                return value; // 不支持的类型，返回原始字符串
            }
        } catch (NumberFormatException e) {
            log.error("[DCC] 类型转换失败 - BeanName: {}, DataId: {}, Key: {}, Value: {}, TargetType: {}", beanName, dataId
                    , fullKey, maskSensitiveValue(fullKey, value), type.getName(), e);
            return getDefaultValueForType(type); // 转换失败，返回类型默认值
        }
    }

    /**
     * 增强布尔值解析：支持 true/false、1/0、yes/no
     *
     * @param value 原始字符串值
     * @return 解析后的布尔值
     */
    private boolean parseBoolean(String value) {
        String lowerValue = value
                .trim()
                .toLowerCase();
        return "true".equals(lowerValue) || "1".equals(lowerValue) || "yes".equals(lowerValue);
    }

    /**
     * 获取指定类型的默认值（处理基本类型和集合类型）
     *
     * @param type 目标类型
     * @return 类型对应的默认值
     */
    private Object getDefaultValueForType(Class<?> type) {
        // 基本类型默认值
        if (type.isPrimitive()) {
            if (type == int.class) return 0;
            if (type == long.class) return 0L;
            if (type == boolean.class) return false;
            if (type == double.class) return 0.0D;
            if (type == float.class) return 0.0F;
            if (type == short.class) return (short) 0;
            if (type == byte.class) return (byte) 0;
        }
        // 集合类型默认值（空集合）
        else if (List.class.isAssignableFrom(type)) {
            return new ArrayList<>();
        } else if (Set.class.isAssignableFrom(type)) {
            return new HashSet<>();
        }
        // 字符串数组默认值（空数组）
        else if (type.isArray() && type.getComponentType() == String.class) {
            return new String[0];
        }
        // 引用类型默认值：null
        return null;
    }

    /**
     * 敏感字段值脱敏：匹配敏感关键词的字段值，只保留首尾2位，中间用******替换
     *
     * @param fieldName 字段名
     * @param value     原始值
     * @return 脱敏后的值
     */
    private String maskSensitiveValue(String fieldName, String value) {
        if (StringUtils.isBlank(value)) {
            return value;
        }

        // 忽略大小写匹配敏感关键词
        String lowerFieldName = fieldName.toLowerCase();
        boolean isSensitive = SENSITIVE_FIELD_KEYWORDS
                .stream()
                .anyMatch(lowerFieldName::contains);
        if (!isSensitive) {
            return value; // 非敏感字段，返回原值
        }

        // 脱敏规则：长度<=4 全隐藏；否则保留首尾2位
        if (value.length() <= 4) {
            return "******";
        }
        return value.substring(0, 2) + "******" + value.substring(value.length() - 2);
    }

    /**
     * 带重试的Nacos配置读取（修复：移除错误的FAILURE常量，内部捕获异常并返回null）
     *
     * @param dataId Nacos配置DataId
     * @param group  Nacos配置Group
     * @return 配置内容（重试失败返回null）
     */
    private String getConfigWithRetry(String dataId, String group) {
        NacosException lastException = null;
        // 重试读取配置
        for (int i = 0; i < NACOS_RETRY_TIMES; i++) {
            try {
                return configService.getConfig(dataId, group, NACOS_CONFIG_TIMEOUT);
            } catch (NacosException e) {
                lastException = e;
                log.warn("[DCC] 读取Nacos配置失败（重试{}次）- DataId: {}, Group: {}", i + 1, dataId, group, e);
                // 重试间隔（避免频繁重试）
                try {
                    Thread.sleep(NACOS_RETRY_INTERVAL);
                } catch (InterruptedException ie) {
                    Thread
                            .currentThread()
                            .interrupt(); // 恢复中断状态
                    break;
                }
            }
        }
        // 重试失败，记录错误日志并返回null
        log.error("[DCC] 读取Nacos配置重试{}次仍失败 - DataId: {}, Group: {}", NACOS_RETRY_TIMES, dataId, group, lastException);
        return null;
    }

    /**
     * 构建Bean唯一标识（用于监听器注册表）
     *
     * @param bean     Bean实例
     * @param beanName Bean名称
     * @return Bean唯一键
     */
    private String buildBeanKey(Object bean, String beanName) {
        return bean
                .getClass()
                .getName() + "_" + beanName;
    }

    /**
     * 构建缓存键前缀（用于清理指定Bean的缓存）
     *
     * @param bean Bean实例
     * @return 缓存键前缀
     */
    private String buildCacheKeyPrefix(Object bean) {
        return bean
                .getClass()
                .getName() + "_";
    }

    /**
     * 构建配置解析缓存键（Bean类名 + DataId + 内容哈希，避免相同内容重复解析）
     *
     * @param bean    Bean实例
     * @param dataId  Nacos配置DataId
     * @param content 配置内容
     * @return 缓存键
     */
    private String buildCacheKey(Object bean, String dataId, String content) {
        return buildCacheKeyPrefix(bean) + dataId + "_" + content.hashCode();
    }

    /**
     * 放入缓存（容量控制：达到上限时清理10%的缓存）
     *
     * @param cacheKey   缓存键
     * @param cacheEntry 缓存条目
     */
    private void putToCache(String cacheKey, CacheEntry cacheEntry) {
        // 缓存容量控制：达到最大容量时，清理前10%的缓存
        if (configContentCache.size() >= CACHE_MAX_SIZE) {
            int removeCount = (int) (CACHE_MAX_SIZE * 0.1);
            List<String> keysToRemove = new ArrayList<>(configContentCache.keySet()).subList(0, removeCount);
            keysToRemove.forEach(configContentCache::remove);
            log.debug("[DCC] 缓存容量达到上限，清理{}条过期缓存", removeCount);
        }
        configContentCache.put(cacheKey, cacheEntry);
    }

    /**
     * 清理过期缓存（遍历缓存，移除过期条目）
     */
    private void cleanExpiredCache() {
        long start = System.currentTimeMillis();
        int removedCount = 0;
        Iterator<Map.Entry<String, CacheEntry>> iterator = configContentCache
                .entrySet()
                .iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, CacheEntry> entry = iterator.next();
            if (entry
                    .getValue()
                    .isExpired()) {
                iterator.remove();
                removedCount++;
            }
        }
        log.debug("[DCC] 清理过期缓存完成 - 清理数量: {}, 耗时: {}ms", removedCount, System.currentTimeMillis() - start);
    }

    /**
     * 启动定时缓存清理任务（每隔CACHE_EXPIRE_MINUTES分钟执行一次）
     */
    private void scheduleCacheCleanup() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "dcc-cache-cleanup-thread");
            thread.setDaemon(true); // 守护线程，不阻塞应用关闭
            return thread;
        });
        // 延迟CACHE_EXPIRE_MINUTES分钟后执行，之后每隔CACHE_EXPIRE_MINUTES分钟执行一次
        scheduler.scheduleAtFixedRate(this::cleanExpiredCache, CACHE_EXPIRE_MINUTES, CACHE_EXPIRE_MINUTES,
                TimeUnit.MINUTES);
    }
}