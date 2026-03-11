package com.c.domain.strategy.repository;

import com.c.domain.strategy.model.entity.StrategyAwardEntity;
import com.c.domain.strategy.model.entity.StrategyEntity;
import com.c.domain.strategy.model.entity.StrategyRuleEntity;
import com.c.domain.strategy.model.vo.*;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;

/**
 * 策略领域仓储接口
 * 核心能力：策略配置查询、概率装配存储、库存原子管理、规则决策树查询
 *
 * @author cyh
 * @date 2026/03/11
 */
public interface IStrategyRepository {

    // ========================================================================
    // 1. 策略配置查询
    // ========================================================================

    /**
     * 查询指定策略下的奖品配置清单
     *
     * @param strategyId 策略ID
     * @return 策略奖品实体列表
     */
    List<StrategyAwardEntity> queryStrategyAwardList(Long strategyId);

    /**
     * 根据策略ID查询策略主体详情
     *
     * @param strategyId 策略ID
     * @return 策略实体对象
     */
    StrategyEntity queryStrategyEntityByStrategyId(Long strategyId);

    /**
     * 查询奖品单体配置详情
     *
     * @param strategyId 策略ID
     * @param awardId    奖品ID
     * @return 奖品实体对象
     */
    StrategyAwardEntity queryStrategyAwardEntity(Long strategyId, Integer awardId);

    /**
     * 获取策略规则实体
     *
     * @param strategyId 策略ID
     * @param ruleModel  规则模型标识
     * @return 策略规则实体
     */
    StrategyRuleEntity queryStrategyRule(Long strategyId, String ruleModel);

    /**
     * 查询策略层级的规则配置值
     *
     * @param strategyId 策略ID
     * @param ruleModel  规则模型标识
     * @return 规则配置值字符串
     */
    String queryStrategyRuleValue(Long strategyId, String ruleModel);

    /**
     * 查询奖品层级的规则配置值
     *
     * @param strategyId 策略ID
     * @param awardId    奖品ID
     * @param ruleModel  规则模型标识
     * @return 规则具体配置
     */
    String queryStrategyRuleValue(Long strategyId, Integer awardId, String ruleModel);

    // ========================================================================
    // 2. 概率装配与寻址
    // ========================================================================

    /**
     * 存储概率查找表（O(1)算法）
     *
     * @param key       策略装配唯一标识
     * @param rateRange 概率总量程
     * @param table     随机索引-奖品ID映射表
     */
    void storeStrategyAwardSearchRateTable(String key, Integer rateRange, Map<Integer, Integer> table);

    /**
     * 存储区间概率查找表（O(LogN)算法）
     *
     * @param key       策略装配唯一标识
     * @param rateRange 总概率量程
     * @param rangeMap  区间上限-奖品ID有序映射表
     */
    void storeStrategyAwardSearchRateTable(String key, Integer rateRange, NavigableMap<Integer, Integer> rangeMap);

    /**
     * 获取指定策略的随机数寻址上限
     *
     * @param key 策略装配Key
     * @return 概率范围最大值
     */
    int getRateRange(String key);

    /**
     * 执行O(1)随机索引寻址
     *
     * @param key     策略装配Key
     * @param rateKey 随机生成的索引值
     * @return 命中的奖品ID
     */
    Integer getStrategyAwardAssemble(String key, int rateKey);

    /**
     * 获取O(LogN)预排序区间查找表
     *
     * @param key 策略装配Key
     * @return 有序区间映射表
     */
    NavigableMap<Integer, Integer> getRangeMap(String key);

    // ========================================================================
    // 3. 决策树模型
    // ========================================================================

    /**
     * 检索规则决策树聚合对象
     *
     * @param treeId 决策树唯一标识
     * @return 决策树全量配置VO
     */
    RuleTreeVO queryRuleTreeVOByTreeId(String treeId);

    /**
     * 查询奖品挂载的规则模型标识
     *
     * @param strategyId 策略ID
     * @param awardId    奖品ID
     * @return 奖品规则模型VO
     */
    StrategyAwardRuleModelVO queryStrategyAwardRuleModel(Long strategyId, Integer awardId);

    // ========================================================================
    // 4. 库存管理与异步一致性
    // ========================================================================

    /**
     * 初始化奖品库存至Redis原子计数器
     *
     * @param cacheKey   库存缓存Key
     * @param awardCount 库存总量
     */
    void cacheStrategyAwardCount(String cacheKey, Integer awardCount);

    /**
     * 分布式原子库存扣减（带过期策略）
     *
     * @param cacheKey    库存标识Key
     * @param endDateTime 活动结束时间
     * @return true-扣减成功，false-失败
     */
    Boolean subtractAwardStock(String cacheKey, Date endDateTime);

    /**
     * 分布式原子库存扣减（标准模式）
     *
     * @param cacheKey 库存标识Key
     * @return true-扣减成功，false-失败
     */
    Boolean subtractAwardStock(String cacheKey);

    /**
     * 写入库存消耗异步流水队列
     *
     * @param strategyAwardStockKeyVO 库存流水标识对象
     */
    void awardStockConsumeSendQueue(StrategyAwardStockKeyVO strategyAwardStockKeyVO);

    /**
     * 从队列中获取待处理的库存流水
     *
     * @return 库存流水标识对象
     */
    StrategyAwardStockKeyVO takeQueueValue();

    /**
     * 持久化物理库存：同步扣减至数据库
     *
     * @param strategyId 策略ID
     * @param awardId    奖品ID
     */
    void updateStrategyAwardStock(Long strategyId, Integer awardId);

    // ========================================================================
    // 5. 业务映射与行为统计
    // ========================================================================

    /**
     * 根据活动ID查询关联策略ID
     *
     * @param activityId 活动唯一标识
     * @return 关联的策略ID
     */
    Long queryStrategyIdByActivityId(Long activityId);

    /**
     * 查询用户当日累计抽奖次数
     *
     * @param userId     用户ID
     * @param strategyId 策略ID
     * @return 今日已抽奖次数
     */
    Integer queryTodayUserRaffleCount(String userId, Long strategyId);

    /**
     * 查询奖品规则锁定门槛次数
     *
     * @param treeIds 规则树ID数组
     * @return 树ID-锁定次数映射Map
     */
    Map<String, Integer> queryAwardRuleLockCount(String[] treeIds);

    /**
     * 查询用户累计抽奖次数
     *
     * @param userId     用户ID
     * @param strategyId 策略ID
     * @return 累计抽奖总数
     */
    Integer queryTotalUserRaffleCount(String userId, Long strategyId);

    /**
     * 查询策略对应的奖品权重规则配置
     *
     * @param strategyId 策略ID
     * @return 权重规则VO列表
     */
    List<RuleWeightVO> queryAwardRuleWeight(Long strategyId);

    /**
     * 缓存策略装配算法名称
     *
     * @param key               缓存键
     * @param algorithmBeanName 算法Bean名称
     */
    void cacheStrategyArmoryAlgorithm(String key, String algorithmBeanName);

    /**
     * 从缓存查询策略装配算法名称
     *
     * @param key 缓存键
     * @return 算法Bean名称
     */
    String queryStrategyArmoryAlgorithmFromCache(String key);
}