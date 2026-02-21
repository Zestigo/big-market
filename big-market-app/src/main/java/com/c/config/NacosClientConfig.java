package com.c.config;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;

/**
 * Nacos 客户端核心配置类
 * 核心职责：
 * 1. 加载 Nacos 客户端配置属性（{@link NacosClientConfigProperties}）
 * 2. 初始化 Nacos ConfigService 核心实例，建立与 Nacos 服务端的物理连接
 * 3. 处理服务地址兼容、命名空间、性能参数等关键配置的封装
 *
 * @author cyh
 * @date 2026/02/21
 * @see NacosClientConfigProperties Nacos 配置属性映射类
 * @see ConfigService Nacos 配置服务核心接口
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(NacosClientConfigProperties.class)
public class NacosClientConfig {

    /** Nacos 客户端配置属性映射实例（由 Spring 自动注入） */
    private final NacosClientConfigProperties properties;

    /**
     * 构造函数注入 Nacos 配置属性
     *
     * @param properties Nacos 客户端配置属性实例
     */
    public NacosClientConfig(NacosClientConfigProperties properties) {
        this.properties = properties;
    }

    /**
     * 创建并初始化 Nacos ConfigService Bean 实例
     * 配置加载逻辑：
     * 1. 服务地址兼容处理：优先使用 serverAddr，兜底使用 registry，自动过滤协议头（如 http://）
     * 2. 命名空间配置：非空时设置，为空则使用 Nacos 默认命名空间
     * 3. 性能参数配置：设置长轮询超时时间、最大重试次数
     *
     * @return 初始化完成的 ConfigService 实例（Spring 单例管理）
     * @throws NacosException 初始化失败时抛出（如地址为空、服务不可达等）
     */
    @Bean(name = "nacosConfigService")
    public ConfigService createConfigService() throws NacosException {
        // 构建 Nacos SDK 所需的配置参数集
        Properties nacosProps = new Properties();

        // 1. 服务地址解析：兼容 serverAddr 和 registry 两种配置，优先使用 serverAddr
        String serverAddr = (properties.getServerAddr() != null && !properties
                .getServerAddr()
                .isEmpty()) ? properties.getServerAddr() : properties.getRegistry();

        // 地址净化：过滤协议头（如 http://、https://），确保仅保留 IP:Port 格式的纯净地址
        if (serverAddr != null && serverAddr.contains("://")) {
            serverAddr = serverAddr.substring(serverAddr.indexOf("://") + 3);
        }

        // 校验核心参数：服务地址为空时直接抛出参数非法异常
        if (serverAddr == null || serverAddr.isEmpty()) {
            throw new NacosException(NacosException.CLIENT_INVALID_PARAM, "Nacos ServerAddr is blank!");
        }
        nacosProps.setProperty("serverAddr", serverAddr);

        // 2. 命名空间配置：非空时设置，为空则使用 Nacos 默认命名空间（public）
        if (properties.getNamespace() != null && !properties
                .getNamespace()
                .isEmpty()) {
            nacosProps.setProperty("namespace", properties.getNamespace());
        }

        // 3. 性能参数配置：设置长轮询超时时间、最大重试次数（转为字符串适配 Properties 格式）
        nacosProps.setProperty("configLongPollTimeout", String.valueOf(properties.getConfigTimeout()));
        nacosProps.setProperty("maxRetry", String.valueOf(properties.getMaxRetries()));

        try {
            // 初始化 Nacos ConfigService 实例，建立与服务端的物理连接
            ConfigService configService = NacosFactory.createConfigService(nacosProps);
            log.info("DCC 物理连接初始化成功 -> ServerAddr: {}, Namespace: {}", serverAddr, properties.getNamespace());
            return configService;
        } catch (NacosException e) {
            // 记录错误日志并向上抛出异常，交由 Spring 容器处理初始化失败
            log.error("DCC 物理连接初始化失败！请检查 Nacos 服务地址/状态或配置参数。ServerAddr: {}", serverAddr, e);
            throw e;
        }
    }
}