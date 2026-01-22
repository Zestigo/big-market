package com.c.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.codec.JsonJacksonCodec;
import org.redisson.config.Config;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.Resource;

/**
 * Redis 客户端配置
 * 方案二：采用 Redisson 自带的 JsonJacksonCodec。
 * 优势：Jackson 对 Java 嵌套泛型（如 Map, List）的支持极其稳定，
 * 能够自动处理多态类型的序列化与反序列化，彻底解决 Fastjson 导致的还原失败问题。
 */
@Configuration
@EnableConfigurationProperties(RedisClientConfigProperties.class)
public class RedisClientConfig {

    @Resource
    private RedisClientConfigProperties properties;

    @Bean("redissonClient")
    public RedissonClient redissonClient(ConfigurableApplicationContext applicationContext) {
        Config config = new Config();

        // 使用 Redisson 默认的 JsonJacksonCodec 替代自定义 Fastjson Codec
        // 这种编解码器会自动处理对象的类型标记，确保嵌套 Map 能够正确还原
        config.setCodec(new JsonJacksonCodec());

        config.useSingleServer()
              .setAddress("redis://" + properties.getHost() + ":" + properties.getPort())
              .setPassword(properties.getPassword())
              .setConnectionPoolSize(properties.getPoolSize())
              .setConnectionMinimumIdleSize(properties.getMinIdleSize())
              .setIdleConnectionTimeout(properties.getIdleTimeout())
              .setConnectTimeout(properties.getConnectTimeout())
              .setRetryAttempts(properties.getRetryAttempts())
              .setRetryInterval(properties.getRetryInterval())
              .setPingConnectionInterval(properties.getPingInterval())
              .setKeepAlive(properties.isKeepAlive());

        return Redisson.create(config);
    }
}