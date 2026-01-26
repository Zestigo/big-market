package com.c.domain.strategy.repository;

import com.c.domain.strategy.model.entity.StrategyAwardEntity;
import com.c.domain.strategy.model.entity.StrategyEntity;
import com.c.domain.strategy.model.entity.StrategyRuleEntity;
import com.c.domain.strategy.model.vo.*;

import java.util.List;
import java.util.Map;

/**
 * 策略领域仓储接口
 * * 职责：
 * 1. 隔离领域层与基础层，封装对 MySQL、Redis 等存储介质的操作细节。
 * 2. 提供领域模型（Entity/VO）的持久化与查询能力。
 * 3. 管理抽奖概率表的装配、库存原子操作及异步补偿任务。
 *
 * @author cyh
 * @date 2026/01/18
 */
public interface IStrategyRepository {

    /**
     * 查询指定策略下的所有奖品配置列表
     *
     * @param strategyId 策略ID
     * @return 奖品实体列表（包含概率、奖品ID、库存等）
     */
    List<StrategyAwardEntity> queryStrategyAwardList(Long strategyId);

    /**
     * 存储抽奖概率查找表
     *
     *
     * @param key                                  查找表唯一标识（如：strategyId 或 strategyId_weight）
     * @param rateRange                            概率范围值（总刻度，如100、1000、10000）
     * @param shuffleStrategyAwardSearchRateTables 乱序后的映射关系表（Key: 随机索引, Value: 奖品ID）
     */
    void storeStrategyAwardSearchRateTable(String key, Integer rateRange, Map<Integer, Integer> shuffleStrategyAwardSearchRateTables);

    /**
     * 获取指定策略的概率刻度范围
     *
     * @param key 查找表标识
     * @return 总刻度值（用于生成随机数的上限）
     */
    int getRateRange(String key);

    /**
     * 执行概率抽取：通过随机索引获取预装配的奖品ID
     *
     * @param key     查找表标识
     * @param rateKey 生成的随机索引值
     * @return 对应的奖品ID
     */
    Integer getStrategyAwardAssemble(String key, int rateKey);

    /**
     * 查询策略层级的规则配置值
     *
     * @param strategyId 策略ID
     * @param ruleModel  规则模型标识（如：rule_weight, rule_blacklist）
     * @return 规则配置的原始字符串数据
     */
    String queryStrategyRuleValue(Long strategyId, String ruleModel);

    /**
     * 查询奖品层级的规则配置值（重载）
     *
     * @param strategyId 策略ID
     * @param awardId    奖品ID
     * @param ruleModel  规则模型标识（如：rule_luck_award）
     * @return 规则配置的原始字符串数据
     */
    String queryStrategyRuleValue(Long strategyId, Integer awardId, String ruleModel);

    /**
     * 查询策略实体配置
     *
     * @param strategyId 策略ID
     * @return 策略主体信息（描述、绑定的规则链等）
     */
    StrategyEntity queryStrategyEntityByStrategyId(Long strategyId);

    /**
     * 查询特定的策略规则详情
     *
     * @param strategyId 策略ID
     * @param ruleModel  规则模型标识
     * @return 规则实体信息（类型、配置值等）
     */
    StrategyRuleEntity queryStrategyRule(Long strategyId, String ruleModel);

    /**
     * 查询规则决策树视图
     *
     * @param treeId 决策树唯一标识
     * @return 决策树 VO（包含根节点、所有节点及其连线逻辑）
     */
    RuleTreeVO queryRuleTreeVOByTreeId(String treeId);

    /**
     * 查询奖品关联的规则模型标识
     *
     * @param strategyId 策略ID
     * @param awardId    奖品ID
     * @return 包含该奖品挂载的规则模型（如绑定的树ID）
     */
    StrategyAwardRuleModelVO queryStrategyAwardRuleModel(Long strategyId, Integer awardId);

    /**
     * 缓存/预热奖品实时库存
     *
     * @param cacheKey   库存缓存唯一Key
     * @param awardCount 初始库存数量
     */
    void cacheStrategyAwardCount(String cacheKey, Integer awardCount);

    /**
     * 原子扣减奖品缓存库存
     *
     *
     * @param cacheKey 库存缓存唯一Key
     * @return 是否扣减成功（true: 扣减成功且有余量；false: 库存不足）
     */
    Boolean subtractAwardStock(String cacheKey);

    /**
     * 发送库存扣减通知到异步队列（用于削峰填谷）
     *
     * @param strategyAwardStockKeyVO 库存记录传输对象
     */
    void awardStockConsumeSendQueue(StrategyAwardStockKeyVO strategyAwardStockKeyVO);

    /**
     * 从异步队列中获取库存消耗任务（用于搬运到数据库）
     *
     * @return 待处理的库存更新任务对象
     */
    StrategyAwardStockKeyVO takeQueueValue();

    /**
     * 物理更新数据库中的奖品库存记录
     *
     * @param strategyId 策略ID
     * @param awardId    奖品ID
     */
    void updateStrategyAwardStock(Long strategyId, Integer awardId);

    StrategyAwardEntity queryStrategyAwardEntity(Long strategyId, Integer awardId);
}