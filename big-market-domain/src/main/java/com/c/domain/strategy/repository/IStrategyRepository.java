package com.c.domain.strategy.repository;

import com.c.domain.strategy.model.entity.StrategyAwardEntity;
import com.c.domain.strategy.model.entity.StrategyEntity;
import com.c.domain.strategy.model.entity.StrategyRuleEntity;
import com.c.domain.strategy.model.vo.*;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 策略领域仓储接口
 * 1. 架构隔离：连接领域层与基础层，确保业务逻辑独立于 MySQL/Redis 等具体实现。
 * 2. 模型防腐：负责将持久化对象 (PO) 转化为领域实体或值对象 (Entity/VO)。
 * 3. 性能支撑：管理高并发下的概率查找表装配及 Redis 原子操作。
 *
 * @author cyh
 * @since 2026/01/18
 */
public interface IStrategyRepository {

    // ========================================================================
    // 1. 策略配置查询 (Configuration Query)
    // ========================================================================

    /**
     * 查询指定策略下的奖品配置清单
     *
     * @param strategyId 策略 ID
     * @return {@link List<StrategyAwardEntity>} 策略奖品实体列表
     */
    List<StrategyAwardEntity> queryStrategyAwardList(Long strategyId);

    /**
     * 根据策略 ID 查询策略主体详情
     *
     * @param strategyId 策略 ID
     * @return {@link StrategyEntity} 策略实体对象
     */
    StrategyEntity queryStrategyEntityByStrategyId(Long strategyId);

    /**
     * 查询奖品单体配置详情
     *
     * @param strategyId 策略 ID
     * @param awardId    奖品 ID
     * @return {@link StrategyAwardEntity} 奖品实体对象
     */
    StrategyAwardEntity queryStrategyAwardEntity(Long strategyId, Integer awardId);

    /**
     * 获取策略规则实体（如：抽奖次数限制规则详情）
     *
     * @param strategyId 策略 ID
     * @param ruleModel  规则模型标识
     * @return {@link StrategyRuleEntity} 策略规则实体
     */
    StrategyRuleEntity queryStrategyRule(Long strategyId, String ruleModel);

    /**
     * 查询策略层级的规则配置值
     *
     * @param strategyId 策略 ID
     * @param ruleModel  规则模型标识 (如：rule_weight)
     * @return 规则阈值配置字符串
     */
    String queryStrategyRuleValue(Long strategyId, String ruleModel);

    /**
     * 查询奖品层级的规则配置值
     *
     * @param strategyId 策略 ID
     * @param awardId    奖品 ID
     * @param ruleModel  规则模型标识
     * @return 规则具体配置
     */
    String queryStrategyRuleValue(Long strategyId, Integer awardId, String ruleModel);

    // ========================================================================
    // 2. 概率装配与寻址 (Assembly & Dispatch)
    // ========================================================================

    /**
     * 存储概率查找表
     * 采用空间换时间，将概率区间映射为 Redis Hash 索引，实现 O(1) 随机寻址。
     *
     * @param key       查找表唯一标识 (strategyId + ruleWeight)
     * @param rateRange 概率总刻度 (如 10000)
     * @param table     索引映射表 (Key: 随机索引, Value: 奖品 ID)
     */
    void storeStrategyAwardSearchRateTable(String key, Integer rateRange, Map<Integer, Integer> table);

    /**
     * 获取指定策略的随机数寻址上限
     *
     * @param key 策略装配 Key
     * @return 概率范围最大值
     */
    int getRateRange(String key);

    /**
     * 执行随机索引寻址
     *
     * @param key     概率表标识
     * @param rateKey 随机生成的索引值 (0 到 rateRange 之间)
     * @return 命中的奖品 ID
     */
    Integer getStrategyAwardAssemble(String key, int rateKey);

    // ========================================================================
    // 3. 决策树模型 (Rule Tree)
    // ========================================================================

    /**
     * 检索规则决策树聚合对象
     * 将扁平数据构建为包含根节点、动作节点及连线的拓扑结构。
     *
     * @param treeId 决策树唯一标识
     * @return {@link RuleTreeVO} 决策树全量配置
     */
    RuleTreeVO queryRuleTreeVOByTreeId(String treeId);

    /**
     * 查询奖品挂载的规则模型标识
     *
     * @param strategyId 策略 ID
     * @param awardId    奖品 ID
     * @return {@link StrategyAwardRuleModelVO} 奖品规则模型 VO
     */
    StrategyAwardRuleModelVO queryStrategyAwardRuleModel(Long strategyId, Integer awardId);

    // ========================================================================
    // 4. 库存管理与异步一致性 (Stock & Consistency)
    // ========================================================================

    /**
     * 缓存热加载：初始化奖品库存至 Redis 原子计数器
     *
     * @param cacheKey   库存 Key
     * @param awardCount 库存值
     */
    void cacheStrategyAwardCount(String cacheKey, Integer awardCount);

    /**
     * 分布式原子库存扣减（带自动续期/过期策略）
     * 1. 采用 Redis + Lua 脚本实现“比较并扣减”的原子操作，防止并发超卖。
     * 2. 利用 endDateTime 判定缓存时效：若 key 首次创建或需更新，则参考活动结束时间设置 TTL。
     * 3. 扣减成功后，需配合发送任务至延迟队列（如：takeQueueValue），确保数据库物理库存最终一致。
     *
     * @param cacheKey    库存标识 Key (例如：strategy_award_stock_key_{strategyId}_{awardId})
     * @param endDateTime 活动结束时间，用于兜底缓存的过期策略，防止库存 key 长期占用内存资源
     * @return 扣减结果：true - 扣减成功且库存充裕；false - 库存不足或已售罄
     */
    Boolean subtractAwardStock(String cacheKey, Date endDateTime);

    /**
     * 分布式原子库存扣减（标准模式）
     * 适用场景：已完成预热（Armory）的库存 Key 扣减。
     * 逻辑：直接执行 Lua 脚本进行原子递减（DECR/LUA）。该操作完全基于 Redis 内存，具备高性能响应能力。
     *
     * @param cacheKey 库存标识 Key
     * @return 扣减结果：true - 扣减成功；false - 库存不足
     */
    Boolean subtractAwardStock(String cacheKey);

    /**
     * 写入库存消耗异步流水队列
     * 采用削峰填谷模式，缓解数据库高频更新压力。
     *
     * @param strategyAwardStockKeyVO 库存流水标识
     */
    void awardStockConsumeSendQueue(StrategyAwardStockKeyVO strategyAwardStockKeyVO);

    /**
     * 从队列中获取待处理的库存流水 (Job 同步任务使用)
     *
     * @return {@link StrategyAwardStockKeyVO} 库存流水标识
     */
    StrategyAwardStockKeyVO takeQueueValue();

    /**
     * 持久化物理库存：将扣减结果同步回数据库
     *
     * @param strategyId 策略 ID
     * @param awardId    奖品 ID
     */
    void updateStrategyAwardStock(Long strategyId, Integer awardId);

    // ========================================================================
    // 5. 业务映射与行为统计 (Mapping & Statistics)
    // ========================================================================

    /**
     * 根据活动 ID 路由关联的策略 ID
     *
     * @param activityId 活动唯一标识
     * @return 关联的策略 ID
     */
    Long queryStrategyIdByActivityId(Long activityId);

    /**
     * 查询用户当日累计抽奖次数
     * 常用于 RuleLock 次数锁判断。
     *
     * @param userId     用户唯一标识
     * @param strategyId 策略 ID
     * @return 当日抽奖计数值
     */
    Integer queryTodayUserRaffleCount(String userId, Long strategyId);

    /**
     * 批量查询奖品规则锁定门槛次数
     *
     * @param treeIds 规则树 ID 数组
     * @return Key: 树ID, Value: 锁定次数值
     */
    Map<String, Integer> queryAwardRuleLockCount(String[] treeIds);
}