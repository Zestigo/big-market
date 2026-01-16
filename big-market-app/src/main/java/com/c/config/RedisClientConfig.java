package com.c.config;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.BaseCodec;
import org.redisson.client.protocol.Decoder;
import org.redisson.client.protocol.Encoder;
import org.redisson.config.Config;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.Resource;
import java.io.IOException;
import javax.annotation.Resource;

/**
 * Redis 客户端，使用 Redisson <a href="https://github.com/redisson/redisson">Redisson</a>
 *
 * @author cyh
 * @date 2026/01/15
 */
@Configuration // Spring配置类
@EnableConfigurationProperties(RedisClientConfigProperties.class) // 启用配置属性
public class RedisClientConfig {

    @Resource
    private RedisClientConfigProperties properties;

    @Bean("redissonClient") // 注册RedissonClient Bean
    public RedissonClient redissonClient(ConfigurableApplicationContext applicationContext) {
        Config config = new Config();
        // 自定义编解码器（用FastJSON序列化/反序列化）
        config.setCodec(new RedisCodec());

        // 单机Redis配置（集群需改config.useClusterServers()）
        config.useSingleServer().setAddress("redis://" + properties.getHost() + ":" + properties.getPort()).setPassword(properties.getPassword())
                // 无密码则注释
                .setConnectionPoolSize(properties.getPoolSize()).setConnectionMinimumIdleSize(properties.getMinIdleSize()).setIdleConnectionTimeout(properties.getIdleTimeout()).setConnectTimeout(properties.getConnectTimeout()).setRetryAttempts(properties.getRetryAttempts()).setRetryInterval(properties.getRetryInterval()).setPingConnectionInterval(properties.getPingInterval()).setKeepAlive(properties.isKeepAlive());

        return Redisson.create(config);
    }

    // 自定义编解码器：解决Redis序列化/反序列化问题
    static class RedisCodec extends BaseCodec {
        private final Encoder encoder = in -> {
            ByteBuf out = ByteBufAllocator.DEFAULT.buffer();
            try {
                ByteBufOutputStream os = new ByteBufOutputStream(out);
                JSON.writeJSONString(os, in, SerializerFeature.WriteClassName);
                return os.buffer();
            } catch (IOException e) {
                out.release();
                throw e;
            } catch (Exception e) {
                out.release();
                throw new IOException(e);
            }
        };

        private final Decoder<Object> decoder = (buf, state) -> JSON.parseObject(new ByteBufInputStream(buf), Object.class);

        @Override
        public Decoder<Object> getValueDecoder() {
            return decoder;
        }

        @Override
        public Encoder getValueEncoder() {
            return encoder;
        }
    }
}