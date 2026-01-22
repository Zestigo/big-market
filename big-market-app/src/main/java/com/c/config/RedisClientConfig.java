package com.c.config;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.parser.Feature;
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

/**
 * Redis 客户端配置
 * 修正说明：通过开启 AutoType 支持，解决 Fastjson 反序列化为 JSONObject 的问题
 */
@Configuration
@EnableConfigurationProperties(RedisClientConfigProperties.class)
public class RedisClientConfig {

    @Resource
    private RedisClientConfigProperties properties;

    @Bean("redissonClient")
    public RedissonClient redissonClient(ConfigurableApplicationContext applicationContext) {
        Config config = new Config();
        // 设置自定义编解码器
        config.setCodec(new RedisCodec());

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

    /**
     * 自定义 Redis 编解码器
     */
    static class RedisCodec extends BaseCodec {

        // 编码器：存入 Redis 时带上 ClassName 标记
        private final Encoder encoder = in -> {
            ByteBuf out = ByteBufAllocator.DEFAULT.buffer();
            try {
                ByteBufOutputStream os = new ByteBufOutputStream(out);
                JSON.writeJSONString(os, in, SerializerFeature.WriteClassName);
                return os.buffer();
            } catch (Exception e) {
                out.release();
                throw new IOException(e);
            }
        };

        // 解码器：从 Redis 读取时，利用 SupportAutoType 识别并还原为原始 Entity
        private final Decoder<Object> decoder = (buf, state) -> {
            try (ByteBufInputStream is = new ByteBufInputStream(buf)) {
                return JSON.parseObject(is, Object.class, Feature.SupportAutoType);
            } catch (Exception e) {
                throw new IOException(e);
            }
        };

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