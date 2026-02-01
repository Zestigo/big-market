package com.c.domain.strategy.repository;

import com.c.domain.strategy.model.entity.StrategyAwardEntity;
import com.c.domain.strategy.model.entity.StrategyEntity;
import com.c.domain.strategy.model.entity.StrategyRuleEntity;
import com.c.domain.strategy.model.vo.*;

import java.util.List;
import java.util.Map;

/**
 * 策略领域仓储接口 (Strategy Domain Repository)
 * * 核心职责：
 * 1. 架构隔离：连接领域层与基础设施层，确保领域层业务逻辑独立于 MySQL、Redis 等具体存储实现。
 * 2. 模型防腐：定义标准的入参和出参，负责将持久化对象 (PO) 转化为具备业务含义的领域模型 (Entity/VO)。
 * 3. 性能支撑：管理高并发抽奖场景下的“概率装配表”索引及“Redis 预减库存”原子操作。
 * 4. 异步保障：提供延迟任务队列的读写接口，保障缓存库存与物理数据库库存的最终一致性。
 *
 * @author cyh
 * @date 2026/01/18
 */
public interface IStrategyRepository {

    /**
     * 查询策略关联的全量奖品配置
     * * 业务场景：用于初始化抽奖引擎或展示活动奖品明细。
     *
     * @param strategyId 策略唯一标识 ID
     * @return 包含概率权重、库存总量、剩余库存及排序信息的奖品领域实体列表
     */
    List<StrategyAwardEntity> queryStrategyAwardList(Long strategyId);

    /**
     * 预装配抽奖概率索引查找表
     * * 设计原理：
     * 通过“空间换时间”的策略，将复杂的百分比/万分比区间计算，转化为 Redis Hash 结构的 O(1) 索引查询。
     *
     * @param key                                  查找表唯一标识（例如：策略ID 或 具备权重标识的组合Key）
     * @param rateRange                            概率总刻度（如 10000 对应万分位精度，决定生成的随机数上限）
     * @param shuffleStrategyAwardSearchRateTables 经过乱序洗牌算法后的映射关系表（Key: 0-rateRange 间的随机索引, Value: 对应的奖品ID）
     */
    void storeStrategyAwardSearchRateTable(String key, Integer rateRange, Map<Integer, Integer> shuffleStrategyAwardSearchRateTables);

    /**
     * 获取指定策略的随机数寻址上限
     * * 业务场景：在执行随机算法前，确定本次抽奖需要生成的随机数范围。
     *
     * @param key 概率表查找标识
     * @return 该策略装配时定义的总刻度值（决定了概率精度控制范围）
     */
    int getRateRange(String key);

    /**
     * 基于随机索引获取中奖奖品 ID
     * * 核心优势：直接从预装配的 Hash 映射表中取值，避开高并发下的区间遍历计算。
     *
     * @param key     概率表查找标识
     * @param rateKey 本次抽奖生成的随机索引值（落在 0-rateRange 之间）
     * @return 最终命中的奖品唯一标识 ID
     */
    Integer getStrategyAwardAssemble(String key, int rateKey);

    /**
     * 查询策略层级的规则配置值
     * * 应用场景：查询如“抽奖次数达到 N 次”、“黑名单限制”等全局性的校验逻辑值。
     *
     * @param strategyId 策略 ID
     * @param ruleModel  规则模型标识（如：rule_weight, rule_blacklist 等标识字符串）
     * @return 对应规则模型预设的配置内容（通常为 JSON 或特定格式字符串）
     */
    String queryStrategyRuleValue(Long strategyId, String ruleModel);

    /**
     * 查询奖品关联的规则配置值
     * * 重载说明：针对特定奖品的校验逻辑，如“该奖品每日限量发放次数”或“库存清零规则”。
     *
     * @param strategyId 策略 ID
     * @param awardId    特定奖品 ID
     * @param ruleModel  规则模型标识（如：rule_luck_award 等）
     * @return 对应奖品规则的具体配置数据
     */
    String queryStrategyRuleValue(Long strategyId, Integer awardId, String ruleModel);

    /**
     * 查询抽奖策略实体详情
     * * 职责：获取策略的基本描述及该策略下挂载的所有业务规则链模型。
     *
     * @param strategyId 策略 ID
     * @return 策略领域实体（包含规则模型列表及描述信息）
     */
    StrategyEntity queryStrategyEntityByStrategyId(Long strategyId);

    /**
     * 获取特定策略规则的完整实体
     *
     * @param strategyId 策略 ID
     * @param ruleModel  规则模型标识
     * @return 包含规则类型（RuleType）、模型名称、配置值、描述等核心逻辑属性的实体
     */
    StrategyRuleEntity queryStrategyRule(Long strategyId, String ruleModel);

    /**
     * 检索规则决策树聚合对象 (Rule Tree VO)
     * * 数据重组：
     * 将物理表中扁平化的树主体、树节点、节点连线逻辑聚合，重组为内存中立体的树形拓扑结构。
     *
     * @param treeId 决策树唯一标识 ID
     * @return 包含根节点入口、全量节点映射 (NodeMap) 及其关联判断逻辑的决策树对象
     */
    RuleTreeVO queryRuleTreeVOByTreeId(String treeId);

    /**
     * 查询特定奖品挂载的规则模型标识
     * * 作用：判断用户抽中此奖品后，是否需要进一步流转到某个规则决策树进行深度校验。
     *
     * @param strategyId 策略 ID
     * @param awardId    奖品 ID
     * @return 奖品规则关联视图（如关联的 tree_id 等）
     */
    StrategyAwardRuleModelVO queryStrategyAwardRuleModel(Long strategyId, Integer awardId);

    /**
     * 缓存与初始化奖品实时库存（库存热加载）
     * * 机制：使用 Redis 提供的 AtomicLong 等原子结构，将库存总量加载到高速缓存中。
     *
     * @param cacheKey   库存缓存唯一识别 Key（通常由策略 ID 与奖品 ID 拼接）
     * @param awardCount 初始化的总可发放数量
     */
    void cacheStrategyAwardCount(String cacheKey, Integer awardCount);

    /**
     * 原子级库存扣减（高性能分布式控制）
     * * 逻辑说明：
     * 1. 基于 Redis decr 指令执行原子递减。
     * 2. 利用库存数值快照结合 setNx 锁实现“数值锁”，严防并发场景下的库存超卖。
     *
     * @param cacheKey 库存缓存唯一识别 Key
     * @return 扣减结果标识（true: 成功扣减且有余额；false: 已售罄或扣减冲突）
     */
    Boolean subtractAwardStock(String cacheKey);

    /**
     * 发送库存消耗异步流水（平摊 DB 压力）
     * * 设计意图：
     * 抽奖系统执行 Redis 预扣成功后，将流水信息推入阻塞队列，由 Job 异步写回数据库，实现写峰值平滑。
     *
     * @param strategyAwardStockKeyVO 库存扣减关键元数据（策略 ID、奖品 ID 等）传输对象
     */
    void awardStockConsumeSendQueue(StrategyAwardStockKeyVO strategyAwardStockKeyVO);

    /**
     * 消费库存消耗队列中的任务（用于同步持久化）
     * * 执行角色：通常由定时任务 Job 发起调用，用于同步物理库库存水位。
     *
     * @return 待执行的库存持久化指令 VO
     */
    StrategyAwardStockKeyVO takeQueueValue();

    /**
     * 持久化更新数据库中的物理库存
     * * 注意事项：此方法将异步队列中确认扣除的份额最终反映到 strategy_award 表的 surplus 字段。
     *
     * @param strategyId 策略 ID
     * @param awardId    奖品 ID
     */
    void updateStrategyAwardStock(Long strategyId, Integer awardId);

    /**
     * 检索单体奖品配置详情
     * * 场景：用于精确计算某一个特定中奖奖品的各项配置参数。
     *
     * @param strategyId 策略 ID
     * @param awardId    奖品 ID
     * @return 奖品领域实体
     */
    StrategyAwardEntity queryStrategyAwardEntity(Long strategyId, Integer awardId);
}