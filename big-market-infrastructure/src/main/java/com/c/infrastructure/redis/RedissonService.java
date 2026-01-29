package com.c.infrastructure.redis;

import com.c.domain.activity.model.entity.ActivitySkuEntity;
import org.redisson.api.*;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Redis 公共服务实现类 - 基于 Redisson 客户端
 * * 职责：
 * 1. 提供对 Redis 基础数据结构（String, Hash, List, Set, ZSet）的封装。
 * 2. 提供高性能并发工具：原子计数器、分布式锁、布隆过滤器。
 * 3. 支持高级队列特性：阻塞队列、延迟队列（用于库存异步补偿）。
 *
 * @author cyh
 * @date 2026/01/21
 */
@Service("redissonService")
public class RedissonService implements IRedisService {

    @Resource
    private RedissonClient redissonClient;

    /**
     * 设置普通对象（String 类型）
     */
    @Override
    public <T> void setValue(String key, T value) {
        redissonClient.<T>getBucket(key).set(value);
    }

    /**
     * 设置带过期时间的普通对象
     *
     * @param expired 过期时间（毫秒）
     */
    @Override
    public <T> void setValue(String key, T value, long expired) {
        RBucket<T> bucket = redissonClient.getBucket(key);
        bucket.set(value, Duration.ofMillis(expired));
    }

    /**
     * 获取普通对象
     */
    @Override
    public <T> T getValue(String key) {
        return redissonClient.<T>getBucket(key).get();
    }

    /**
     * 获取普通队列
     */
    @Override
    public <T> RQueue<T> getQueue(String key) {
        return redissonClient.getQueue(key);
    }

    /**
     * 获取阻塞队列 - 常用于生产者-消费者模型，take() 方法在队列为空时会阻塞线程
     */
    @Override
    public <T> RBlockingQueue<T> getBlockingQueue(String key) {
        return redissonClient.getBlockingQueue(key);
    }

    /**
     * 获取延迟队列 - 基于阻塞队列包装，实现定时/延迟任务处理
     */
    @Override
    public <T> RDelayedQueue<T> getDelayedQueue(RBlockingQueue<T> rBlockingQueue) {
        return redissonClient.getDelayedQueue(rBlockingQueue);
    }

    /**
     * 原子增加（自增 1）
     */
    @Override
    public long incr(String key) {
        return redissonClient.getAtomicLong(key).incrementAndGet();
    }

    /**
     * 原子增加指定步长
     */
    @Override
    public long incrBy(String key, long delta) {
        return redissonClient.getAtomicLong(key).addAndGet(delta);
    }

    /**
     * 原子减少（自减 1）- 抽奖库存扣减的核心底层实现
     */
    @Override
    public long decr(String key) {
        return redissonClient.getAtomicLong(key).decrementAndGet();
    }

    /**
     * 原子减少指定步长
     */
    @Override
    public long decrBy(String key, long delta) {
        return redissonClient.getAtomicLong(key).addAndGet(-delta);
    }

    /**
     * 删除指定 Key
     */
    @Override
    public void remove(String key) {
        redissonClient.getBucket(key).delete();
    }

    /**
     * 判断 Key 是否存在
     */
    @Override
    public boolean isExists(String key) {
        return redissonClient.getBucket(key).isExists();
    }

    /**
     * 向 Set 集合添加元素（自动去重）
     */
    public void addToSet(String key, String value) {
        RSet<String> set = redissonClient.getSet(key);
        set.add(value);
    }

    /**
     * 判断元素是否存在于 Set 集合中
     */
    public boolean isSetMember(String key, String value) {
        RSet<String> set = redissonClient.getSet(key);
        return set.contains(value);
    }

    /**
     * 向 List 集合添加元素
     */
    public void addToList(String key, String value) {
        RList<String> list = redissonClient.getList(key);
        list.add(value);
    }

    /**
     * 获取 List 中指定索引的元素
     */
    public String getFromList(String key, int index) {
        RList<String> list = redissonClient.getList(key);
        return list.get(index);
    }

    /**
     * 获取操作 Map 结构的句柄
     */
    @Override
    public <K, V> RMap<K, V> getMap(String key) {
        return redissonClient.getMap(key);
    }

    /**
     * 向 Hash 表添加字段
     */
    public void addToMap(String key, String field, String value) {
        RMap<String, String> map = redissonClient.getMap(key);
        map.put(field, value);
    }

    /**
     * 获取 Hash 表指定字段的值
     */
    public String getFromMap(String key, String field) {
        RMap<String, String> map = redissonClient.getMap(key);
        return map.get(field);
    }

    /**
     * 泛型获取 Hash 表指定字段的值（装配概率表时的核心调用）
     */
    @Override
    public <K, V> V getFromMap(String key, K field) {
        return redissonClient.<K, V>getMap(key).get(field);
    }

    /**
     * 向 SortedSet（有序集合）添加元素
     */
    public void addToSortedSet(String key, String value) {
        RSortedSet<String> sortedSet = redissonClient.getSortedSet(key);
        sortedSet.add(value);
    }

    /**
     * 获取分布式可重入锁
     */
    @Override
    public RLock getLock(String key) {
        return redissonClient.getLock(key);
    }

    /**
     * 获取公平锁 - 严格按照请求顺序获取锁
     */
    @Override
    public RLock getFairLock(String key, Integer awardCount) {
        return redissonClient.getFairLock(key);
    }

    /**
     * 获取读写锁
     */
    @Override
    public RReadWriteLock getReadWriteLock(String key) {
        return redissonClient.getReadWriteLock(key);
    }

    /**
     * 获取信号量 - 常用于流量控制、限流
     */
    @Override
    public RSemaphore getSemaphore(String key) {
        return redissonClient.getSemaphore(key);
    }

    /**
     * 获取可过期性信号量
     */
    @Override
    public RPermitExpirableSemaphore getPermitExpirableSemaphore(String key) {
        return redissonClient.getPermitExpirableSemaphore(key);
    }

    /**
     * 获取分布式倒计数锁
     */
    @Override
    public RCountDownLatch getCountDownLatch(String key) {
        return redissonClient.getCountDownLatch(key);
    }

    /**
     * 获取布隆过滤器 - 用于高并发下的缓存穿透保护
     */
    @Override
    public <T> RBloomFilter<T> getBloomFilter(String key) {
        return redissonClient.getBloomFilter(key);
    }

    /**
     * 初始化原子长整型值
     */
    @Override
    public void setAtomicLong(String key, long value) {
        redissonClient.getAtomicLong(key).set(value);
    }

    /**
     * 原子设置 Key（分布式锁实现）
     * * 业务场景：
     * 1. 分布式排他锁：确保在高并发下，只有一个线程能执行特定逻辑（如库存扣减补偿）。
     * 2. 幂等性控制：防止同一业务请求被重复处理。
     * 增加了 30 秒的自动过期时间。这是分布式锁的关键，防止因进程意外宕机导致的“永久死锁”。
     * 30 秒是一个通用的防御性时长，足以覆盖大多数业务逻辑的执行。
     *
     * @param key 锁的唯一标识
     * @return true: 抢锁成功；false: 锁已被占用
     */
    @Override
    public Boolean setNx(String key) {
        // 使用 setIfAbsent 配合过期时间，确保锁的原子性与安全性
        return redissonClient.getBucket(key).setIfAbsent("lock", Duration.ofSeconds(30));
    }

    /**
     * 分布式锁/原子占位实现
     *
     * @param key      锁的唯一标识（通常是业务 ID 或特定操作名）
     * @param expired  过期数值
     * @param timeUnit 时间单位（秒、毫秒等）
     * @return true-成功获取锁/占位成功；false-锁已被占用/占位失败
     */
    @Override
    public Boolean setNx(String key, long expired, TimeUnit timeUnit) {
        // 1. 【单位转换】将传入的各种时间单位（如：秒、分钟）统一转换为毫秒。
        // 这样做可以适配 Java 8 的 Duration API，同时保证在不同单位下计算的精确度。
        long millis = timeUnit.toMillis(expired);

        // 2. 调用 Redisson 的 setIfAbsent 方法。
        // 该方法封装了 Redis 的原子指令：SET key value NX PX milliseconds
        // - NX (Not eXists): 只有当 Key 不存在时才执行设置操作（即“占坑”）。
        // - PX: 设置以毫秒为单位的过期时间。
        //
        // 注意：这里使用 java.time.Duration.ofMillis(millis) 是为了适配 Redisson 的新版 API，
        // 同时也解决了旧版本 trySet 方法被弃用（Deprecated）的问题。
        return redissonClient.getBucket(key).setIfAbsent("lock", Duration.ofMillis(millis));
    }
}