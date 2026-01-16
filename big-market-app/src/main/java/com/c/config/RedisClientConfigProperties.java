package com.c.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data // Lombok自动生成get/set
@ConfigurationProperties(prefix = "redis.sdk.config") // 读取yml中redis.sdk.config前缀的配置
public class RedisClientConfigProperties {
    private String host; // Redis主机
    private int port; // 端口
    private String password; // 密码（无则留空）
    private int poolSize = 64; // 连接池大小
    private int minIdleSize = 10; // 最小空闲连接数
    private int idleTimeout = 10000; // 空闲连接超时
    private int connectTimeout = 10000; // 连接超时
    private int retryAttempts = 3; // 重试次数
    private int retryInterval = 1000; // 重试间隔
    private int pingInterval = 0; // 心跳检测间隔
    private boolean keepAlive = true; // 保持长连接
}