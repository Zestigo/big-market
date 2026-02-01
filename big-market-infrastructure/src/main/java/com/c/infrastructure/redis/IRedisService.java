package com.c.infrastructure.redis;

import com.c.domain.activity.model.entity.ActivitySkuEntity;
import org.redisson.api.*;

import java.util.concurrent.TimeUnit;

/**
 * Redis 基础设施层接口
 * * 职责：
 * 封装 Redisson 客户端操作，为领域层提供统一的分布式缓存、原子计数、分布式锁及异步队列服务。
 * *
 *
 * @author cyh
 * @date 2026/01/21
 */
public interface IRedisService {

    /**
     * 写入基础 KV 数据（String 类型）
     *
     * @param key   键
     * @param value 对象值（支持 POJO 序列化）
     */
    <T> void setValue(String key, T value);

    /**
     * 写入带过期时间的 KV 数据
     *
     * @param key     键
     * @param value   值
     * @param expired 过期时长（单位：毫秒）
     */
    <T> void setValue(String key, T value, long expired);

    /**
     * 获取指定 Key 的值
     *
     * @param key 键
     * @return 存储的对象实体
     */
    <T> T getValue(String key);

    /**
     * 获取普通先进先出（FIFO）队列
     */
    <T> RQueue<T> getQueue(String key);

    /**
     * 获取阻塞队列（Blocking Queue）
     * 业务场景：常用于生产者-消费者模式，消费者通过 poll() 阻塞等待新任务，减轻 CPU 空转。
     */
    <T> RBlockingQueue<T> getBlockingQueue(String key);

    /**
     * 获取延迟队列（Delayed Queue）
     * 业务场景：抽奖库存异步补偿。用户抽奖成功后，消息在延迟队列中等待，到时后自动进入阻塞队列供 Worker 消费。
     */
    <T> RDelayedQueue<T> getDelayedQueue(RBlockingQueue<T> rBlockingQueue);

    /**
     * 原子自增 1
     *
     * @return 自增后的数值
     */
    long incr(String key);

    /**
     * 原子增加指定步长
     */
    long incrBy(String key, long delta);

    /**
     * 原子自减 1
     * 业务场景：秒杀/抽奖库存预扣减的核心操作。利用 Redis 单线程原子性防止库存扣减冲突。
     */
    long decr(String key);

    /**
     * 原子减少指定步长
     */
    long decrBy(String key, long delta);

    /**
     * 移除 Key
     */
    void remove(String key);

    /**
     * 判断 Key 是否存在
     */
    boolean isExists(String key);

    /**
     * 向 Set 集合中添加成员（自动去重）
     */
    void addToSet(String key, String value);

    /**
     * 判断元素是否属于指定 Set 集合
     */
    boolean isSetMember(String key, String value);

    /**
     * 向 List 列表末尾添加元素
     */
    void addToList(String key, String value);

    /**
     * 获取 List 列表中指定索引的值
     */
    String getFromList(String key, int index);

    /**
     * 获取 Hash（散列）映射结构
     * 业务场景：存储抽奖概率装配表（Index -> AwardId）
     */
    <K, V> RMap<K, V> getMap(String key);

    /**
     * 向 Hash 表中写入字段
     */
    void addToMap(String key, String field, String value);

    /**
     * 获取 Hash 表中指定字段的字符串值
     */
    String getFromMap(String key, String field);

    /**
     * 获取 Hash 表中指定字段的泛型值（自动反序列化）
     */
    <K, V> V getFromMap(String key, K field);

    /**
     * 向 SortedSet（有序集合）中添加成员
     */
    void addToSortedSet(String key, String value);

    /**
     * 获取分布式可重入锁（RLock）
     * 业务场景：保护临界资源，支持 Watchdog 自动续期。
     */
    RLock getLock(String key);

    /**
     * 获取分布式公平锁
     * 保证锁的获取顺序按照请求的时间先后顺序执行。
     */
    RLock getFairLock(String key, Integer awardCount);

    /**
     * 获取分布式读写锁
     */
    RReadWriteLock getReadWriteLock(String key);

    /**
     * 获取分布式信号量（Semaphore）
     * 业务场景：高并发限流，控制同时进入抽奖引擎的用户数量。
     */
    RSemaphore getSemaphore(String key);

    /**
     * 获取支持过期的信号量
     */
    RPermitExpirableSemaphore getPermitExpirableSemaphore(String key);

    /**
     * 分布式倒计数闭锁（CountDownLatch）
     */
    RCountDownLatch getCountDownLatch(String key);

    /**
     * 获取布隆过滤器
     * 业务场景：防止恶意请求穿透缓存直接冲击数据库，用于校验策略或奖品是否存在。
     */
    <T> RBloomFilter<T> getBloomFilter(String key);

    /**
     * 初始化原子长整型数值
     * 用于在库存预热时设置初始剩余量。
     */
    void setAtomicLong(String key, long value);

    /**
     * 原子设置 Key（分布式锁简易实现）
     * 业务场景：抢占式操作。在库存扣减逻辑中，通过 setNx 确保只有一个请求能处理特定的库存余量位。
     *
     * @param lockKey 锁标识
     * @return true: 成功获得锁（此前不存在）；false: 获取锁失败
     */
    Boolean setNx(String lockKey);
    /**
     * 设置基础键值对
     *
     * @param key      键
     * @param value    值
     * @param timeout  过期时间
     * @param timeUnit 时间单位（如 TimeUnit.MINUTES）
     */
     <T> void setValue(String key, T value, long timeout, TimeUnit timeUnit);

    Boolean setNx(String key, long expired, TimeUnit timeUnit);
}