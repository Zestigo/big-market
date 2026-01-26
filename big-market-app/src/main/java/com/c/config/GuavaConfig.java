package com.c.config;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Guava 本地缓存配置
 * * 职责：
 * 作为系统 L1 级缓存，降低对 Redis (L2) 或数据库的瞬时读取压力。
 * * 业务背景：
 * 在高并发抽奖场景下，部分热点数据（如策略装配状态、非实时库存标识）无需每次都请求 Redis。
 * *
 *
 * @author cyh
 * @date 2026/01/25
 */
@Configuration
public class GuavaConfig {

    /**
     * 定义一个通用的本地缓存空间
     * * 配置说明：
     * 1. expireAfterWrite(3, TimeUnit.SECONDS): 设置写入 3 秒后过期。
     * - 极短的过期时间确保了数据的一致性，同时在秒杀瞬间能抵挡万级请求。
     * 2. initialCapacity(100): 初始容量。
     * 3. maximumSize(1000): 最大限制，防止 OOM（内存溢出）。
     *
     * @return Cache 实例
     */
    @Bean(name = "cache")
    public Cache<String, String> cache() {
        return CacheBuilder.newBuilder().initialCapacity(100).maximumSize(1000)
                           .expireAfterWrite(3, TimeUnit.SECONDS).build();
    }
}