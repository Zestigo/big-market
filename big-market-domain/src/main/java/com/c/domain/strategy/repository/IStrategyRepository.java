package com.c.domain.strategy.repository;

import com.c.domain.strategy.model.entity.StrategyAwardEntity;
import com.c.domain.strategy.model.entity.StrategyEntity;
import com.c.domain.strategy.model.entity.StrategyRuleEntity;
import com.c.domain.strategy.model.vo.*;

import java.util.List;
import java.util.Map;

/**
 * 策略领域仓储接口 (Strategy Domain Repository)
 * 1. 架构隔离：连接领域层与基础设施层，确保业务逻辑独立于具体的存储实现（MySQL/Redis）。
 * 2. 模型防腐：负责将持久化对象 (PO) 转化为具备业务含义的领域模型 (Entity/VO)。
 * 3. 性能支撑：管理高并发下的“概率装配查找表”及“Redis 原子减库存”操作。
 * 4. 最终一致性：提供异步队列接口，保障缓存库存与物理数据库的同步。
 *
 * @author cyh
 * @date 2026/01/18
 */
public interface IStrategyRepository {

    // ========================================================================
    // 1. 策略装配与概率查询 (Probability & Assembly)
    // ========================================================================

    /**
     * 查询策略关联的全量奖品配置
     *
     * @param strategyId 策略ID
     * @return 奖品领域实体列表（包含权重、库存、排序等）
     */
    List<StrategyAwardEntity> queryStrategyAwardList(Long strategyId);

    /**
     * 存储概率查找表（空间换时间策略）
     * 将百分比/万分比区间计算转化为 Redis Hash 的 O(1) 索引查询。
     *
     * @param key       查找表唯一标识
     * @param rateRange 概率总刻度（如10000）
     * @param table     索引映射表（Key: 随机索引, Value: 奖品ID）
     */
    void storeStrategyAwardSearchRateTable(String key, Integer rateRange, Map<Integer, Integer> table);

    /**
     * 获取指定策略的随机数寻址上限
     */
    int getRateRange(String key);

    /**
     * 执行随机索引寻址
     *
     * @param key     概率表标识
     * @param rateKey 随机生成的索引值
     * @return 命中的奖品 ID
     */
    Integer getStrategyAwardAssemble(String key, int rateKey);

    // ========================================================================
    // 2. 规则链与规则值查询 (Rule Configuration)
    // ========================================================================

    /**
     * 查询策略层级的规则配置值（如：rule_weight 的阈值配置）
     */
    String queryStrategyRuleValue(Long strategyId, String ruleModel);

    /**
     * 查询奖品层级的规则配置值（针对特定奖品的校验逻辑）
     */
    String queryStrategyRuleValue(Long strategyId, Integer awardId, String ruleModel);

    /**
     * 查询策略实体详情（包含该策略下挂载的所有规则模型）
     */
    StrategyEntity queryStrategyEntityByStrategyId(Long strategyId);

    /**
     * 获取特定规则的完整实体对象
     */
    StrategyRuleEntity queryStrategyRule(Long strategyId, String ruleModel);

    /**
     * 获取单体奖品配置详情
     */
    StrategyAwardEntity queryStrategyAwardEntity(Long strategyId, Integer awardId);

    // ========================================================================
    // 3. 决策树模型 (Decision Tree / Rule Tree)
    // ========================================================================

    /**
     * 检索规则决策树聚合对象
     * 将扁平化的节点数据重组为内存中的立体拓扑结构。
     *
     * @param treeId 决策树唯一标识
     * @return 包含根节点及全量节点映射的 VO
     */
    RuleTreeVO queryRuleTreeVOByTreeId(String treeId);

    /**
     * 查询奖品挂载的规则模型标识（判断是否需要进入决策树）
     */
    StrategyAwardRuleModelVO queryStrategyAwardRuleModel(Long strategyId, Integer awardId);

    // ========================================================================
    // 4. 库存管理与异步流水 (Stock Management)
    // ========================================================================

    /**
     * 缓存热加载：初始化奖品实时库存到 Redis 原子计数器
     */
    void cacheStrategyAwardCount(String cacheKey, Integer awardCount);

    /**
     * 分布式原子库存扣减
     *
     * @return true: 扣减成功且有余量；false: 库存不足或已售罄
     */
    Boolean subtractAwardStock(String cacheKey);

    /**
     * 写入库存消耗异步流水队列（平摊数据库写压力）
     */
    void awardStockConsumeSendQueue(StrategyAwardStockKeyVO strategyAwardStockKeyVO);

    /**
     * 获取队列中的库存消耗任务（用于 Job 持久化）
     */
    StrategyAwardStockKeyVO takeQueueValue();

    /**
     * 持久化物理库存：将异步结果写回数据库 strategy_award 表
     */
    void updateStrategyAwardStock(Long strategyId, Integer awardId);

    // ========================================================================
    // 5. 跨域关联查询 (Cross-Domain Mapping)
    // ========================================================================

    /**
     * 根据活动 ID 查询关联的策略 ID
     * 建立活动层与策略层的业务映射关系。
     *
     * @param activityId 活动唯一标识
     * @return 关联的策略 ID
     */
    Long queryStrategyIdByActivityId(Long activityId);

    /**
     * 查询用户当日累计抽奖次数
     * 关键业务指标：用于规则树中的“次数锁(RuleLock)”逻辑判断。
     *
     * @param userId     用户唯一标识
     * @param strategyId 策略 ID
     * @return 当日已参与抽奖的计数值
     */
    Integer queryTodayUserRaffleCount(String userId, Long strategyId);
}