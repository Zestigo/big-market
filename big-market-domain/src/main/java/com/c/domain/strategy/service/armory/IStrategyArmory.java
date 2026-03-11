package com.c.domain.strategy.service.armory;

/**
 * 策略装配工厂接口（Strategy Armory Service）
 * 核心职责：
 * 负责抽奖策略的“计算预热”与“数据装配”。将数据库中静态的抽奖规则（概率、权重、奖品）
 * 转化为内存态的高性能查找表，并同步至 Redis 分布式缓存中。
 * 设计思想：
 * 1. 空间换时间：通过生成 O(1) 复杂度的随机索引查找表，消除抽奖时的实时概率计算开销。
 * 2. 动静分离：将高频访问的策略数据从数据库剥离至缓存，保障抽奖调度的高吞吐量。
 *
 * @author cyh
 */
public interface IStrategyArmory {

    /**
     * 核心装配：基于策略 ID 进行全量装配
     * 执行流程：
     * 1. 概率计算：基于奖品概率分布，构建随机索引查找表（通过 Shuffle 算法打散）。
     * 2. 缓存预热：将查找表、奖品元数据、规则权重等关键信息存入分布式缓存。
     * 3. 资源初始化：同步奖品库存至 Redis 计数器，为秒杀级抽奖场景做准备。
     *
     * @param strategyId 策略唯一标识（抽奖配置的核心索引）
     * @return true: 装配成功，策略就绪；false: 配置异常或基础设施（Redis）连接失败
     */
    boolean assembleLotteryStrategy(Long strategyId);

    /**
     * 关联装配：基于活动 ID 触发关联策略的装配
     * * 常用于活动发布或审核通过后的自动化预热场景。
     * 内部逻辑通常先查询活动关联的 strategyId，随后调用相应的装配核心方法。
     *
     * @param activityId 活动配置 ID
     * @return 整体装配链路是否成功执行
     */
    boolean assembleLotteryStrategyByActivityId(Long activityId);
}