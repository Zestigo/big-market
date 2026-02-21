package com.c.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Nacos 客户端配置属性类
 *
 * @author cyh
 * @date 2026/02/18
 */
@Data
@ConfigurationProperties(prefix = "nacos.sdk.config", ignoreInvalidFields = true)
public class NacosClientConfigProperties {

    // 两个变量共存，往往是因为项目同时集成了 Dubbo 和原生 Spring Cloud，为了适配两者的配置习惯才特意分开定义的。
    /* Nacos 注册中心逻辑地址，通常带协议前缀 */
    private String registry;

    /* Nacos 服务端物理通信地址，由IP和端口组成 */
    private String serverAddr;

    /* 命名空间 ID (UUID) */
    private String namespace;

    /* 配置集 ID */
    private String dataId;

    /* 配置分组 */
    private String group = "DEFAULT_GROUP";

    /* 配置读取超时时间 */
    private int configTimeout = 5000;

    /* 最大重试次数 */
    private int maxRetries = 3;

}