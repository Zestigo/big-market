package com.c.infrastructure.adapter.repository;

import com.c.domain.strategy.model.entity.StrategyAwardEntity;
import com.c.domain.strategy.model.entity.StrategyEntity;
import com.c.domain.strategy.model.entity.StrategyRuleEntity;
import com.c.domain.strategy.model.vo.*;
import com.c.domain.strategy.repository.IStrategyRepository;
import com.c.infrastructure.dao.*;
import com.c.infrastructure.po.*;
import com.c.infrastructure.redis.IRedisService;
import com.c.types.common.Constants;
import com.c.types.enums.ResponseCode;
import com.c.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RDelayedQueue;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 策略领域仓储实现服务 (Strategy Domain Repository Implementation)
 * 职责定位：
 * 1. 数据防腐与转换：在基础设施层屏蔽数据库 (MySQL) 与缓存 (Redis) 的技术细节，将持久化对象 (PO) 转化为领域实体 (Entity)。
 * 2. 状态管理：维护抽奖策略的内存镜像、概率查找表以及库存计数器。
 * 3. 异步链路衔接：负责库存扣减流水的入队，支撑 Redis 预扣减与 DB 最终一致性的异步解耦。
 *
 * @author cyh
 * @date 2026/01/20
 */
@Slf4j
@Repository
public class StrategyRepository implements IStrategyRepository {

    @Resource
    private IStrategyDao strategyDao;
    @Resource
    private IStrategyAwardDao strategyAwardDao;
    @Resource
    private IStrategyRuleDao strategyRuleDao;
    @Resource
    private IRedisService redisService;
    @Resource
    private IRuleTreeDao ruleTreeDao;
    @Resource
    private IRuleTreeNodeDao ruleTreeNodeDao;
    @Resource
    private IRuleTreeNodeLineDao ruleTreeNodeLineDao;

    /**
     * 查询策略关联的奖品列表
     * 业务逻辑：
     * 1. 优先检索 Redis 缓存，以支撑高并发下的活动配置读取。
     * 2. 缓存未命中时，访问数据库并进行 PO 到 Entity 的 Builder 模式转换。
     * 3. 将结果回写缓存，确保后续请求的响应性能。
     *
     * @param strategyId 策略ID
     * @return 奖品领域实体列表
     */
    @Override
    public List<StrategyAwardEntity> queryStrategyAwardList(Long strategyId) {
        // 构建缓存键：strategy_award_list_key:策略ID
        String cacheKey = Constants.RedisKey.STRATEGY_AWARD_LIST_KEY + strategyId;

        // 步骤1：尝试从分布式缓存中直接获取已序列化的领域实体
        List<StrategyAwardEntity> strategyAwardEntities = redisService.getValue(cacheKey);
        if (null != strategyAwardEntities && !strategyAwardEntities.isEmpty()) return strategyAwardEntities;

        // 步骤2：缓存失效，从物理库加载持久化配置对象
        List<StrategyAward> strategyAwards = strategyAwardDao.queryStrategyAwardListByStrategyId(strategyId);

        // 步骤3：模型映射转换，将数据库字段映射为领域层可见的业务属性
        strategyAwardEntities = new ArrayList<>(strategyAwards.size());
        for (StrategyAward strategyAward : strategyAwards) {
            StrategyAwardEntity strategyAwardEntity = StrategyAwardEntity.builder()
                                                                         .strategyId(strategyAward.getStrategyId())
                                                                         .awardId(strategyAward.getAwardId())
                                                                         .awardCount(strategyAward.getAwardCount())
                                                                         .awardCountSurplus(strategyAward.getAwardCountSurplus())
                                                                         .awardRate(strategyAward.getAwardRate())
                                                                         .sort(strategyAward.getSort())
                                                                         .awardTitle(strategyAward.getAwardTitle())
                                                                         .awardSubtitle(strategyAward.getAwardSubtitle())
                                                                         .build();
            strategyAwardEntities.add(strategyAwardEntity);
        }

        // 步骤4：刷新缓存，实现数据的“懒加载”同步
        redisService.setValue(cacheKey, strategyAwardEntities);

        return strategyAwardEntities;
    }

    /**
     * 存储抽奖概率查找映射表（装配阶段关键步骤）
     * 核心设计：将概率计算转化为 O(1) 的索引查找，解决复杂区间计算在高并发下的性能瓶颈。
     *
     * @param key 策略装配标识
     * @param rateRange 概率分母（随机数上限，如 10000）
     * @param shuffleStrategyAwardSearchRateTables 打乱后的奖品 ID 分布映射（随机位 -> 奖品ID）
     */
    @Override
    public void storeStrategyAwardSearchRateTable(String key, Integer rateRange,
                                                  Map<Integer, Integer> shuffleStrategyAwardSearchRateTables) {
        // 1. 记录本策略的随机数寻址范围
        redisService.setValue(Constants.RedisKey.STRATEGY_RATE_RANGE_KEY + key, rateRange);

        // 2. 利用 Redis Hash 结构存储映射表，Key 为随机数，Value 为对应的奖品 ID
        // 抽奖时只需：Random(rateRange) -> GetFromHash(RandomValue) -> 获取中奖奖品
        Map<Integer, Integer> cacheRateTable = redisService.getMap(Constants.RedisKey.STRATEGY_RATE_TABLE_KEY + key);
        cacheRateTable.putAll(shuffleStrategyAwardSearchRateTables);
    }

    /**
     * 获取抽奖概率分母（随机数上限）
     *
     * @param key 策略装配标识
     * @return 概率总数
     * @throws AppException 若策略未在 Armory 中装配，则抛出异常，防止非法访问
     */
    @Override
    public int getRateRange(String key) {
        String cacheKey = Constants.RedisKey.STRATEGY_RATE_RANGE_KEY + key;
        if (!redisService.isExists(cacheKey)) {
            log.error("策略概率范围缓存异常，请确认策略是否已装配！key:{}", key);
            throw new AppException(ResponseCode.UN_ASSEMBLED_STRATEGY_ARMORY.getCode(),
                    cacheKey + Constants.COLON + ResponseCode.UN_ASSEMBLED_STRATEGY_ARMORY.getInfo());
        }
        return redisService.getValue(cacheKey);
    }

    /**
     * 执行概率索引查询：根据生成的随机数获取对应中奖奖品
     *
     * @param key 策略装配标识
     * @param rateKey 生成的随机索引值
     * @return 奖品ID
     */
    @Override
    public Integer getStrategyAwardAssemble(String key, int rateKey) {
        return redisService.getFromMap(Constants.RedisKey.STRATEGY_RATE_TABLE_KEY + key, rateKey);
    }

    /**
     * 查询策略规则的具体配置值
     *
     * @param strategyId 策略ID
     * @param awardId 奖品ID（可为 null，表示通用策略规则）
     * @param ruleModel 规则模型标识（如：rule_lock, rule_luck_award）
     * @return 规则配置内容（通常为 JSON 或字符串标识）
     */
    @Override
    public String queryStrategyRuleValue(Long strategyId, Integer awardId, String ruleModel) {
        StrategyRule strategyRule = StrategyRule.builder().strategyId(strategyId).awardId(awardId)
                                                .ruleModel(ruleModel).build();
        return strategyRuleDao.queryStrategyRuleValue(strategyRule);
    }

    @Override
    public String queryStrategyRuleValue(Long strategyId, String ruleModel) {
        return queryStrategyRuleValue(strategyId, null, ruleModel);
    }

    /**
     * 检索策略主体实体信息
     * 包含策略描述及该策略所挂载的所有规则模型清单。
     *
     * @param strategyId 策略ID
     * @return 策略实体（StrategyEntity）
     */
    @Override
    public StrategyEntity queryStrategyEntityByStrategyId(Long strategyId) {
        String cacheKey = Constants.RedisKey.STRATEGY_KEY + strategyId;
        StrategyEntity strategyEntity = redisService.getValue(cacheKey);
        if (null != strategyEntity) return strategyEntity;

        Strategy strategy = strategyDao.queryStrategyEntityByStrategyId(strategyId);
        if (null == strategy) return null;

        strategyEntity = StrategyEntity.builder().strategyId(strategy.getStrategyId())
                                       .strategyDesc(strategy.getStrategyDesc())
                                       .ruleModels(strategy.getRuleModels()).build();

        redisService.setValue(cacheKey, strategyEntity);
        return strategyEntity;
    }

    /**
     * 获取特定的策略规则实体详情
     *
     * @param strategyId 策略ID
     * @param ruleModel 规则标识
     * @return 包含规则类型、描述、配置值的实体对象
     */
    @Override
    public StrategyRuleEntity queryStrategyRule(Long strategyId, String ruleModel) {
        StrategyRule strategyRuleReq = StrategyRule.builder().strategyId(strategyId).ruleModel(ruleModel)
                                                   .build();
        StrategyRule strategyRuleRes = strategyRuleDao.queryStrategyRule(strategyRuleReq);
        if (null == strategyRuleRes) return null;

        return StrategyRuleEntity.builder().strategyId(strategyRuleRes.getStrategyId())
                                 .awardId(strategyRuleRes.getAwardId())
                                 .ruleType(strategyRuleRes.getRuleType())
                                 .ruleModel(strategyRuleRes.getRuleModel())
                                 .ruleValue(strategyRuleRes.getRuleValue())
                                 .ruleDesc(strategyRuleRes.getRuleDesc()).build();
    }

    /**
     * 构建立体化规则决策树模型 (RuleTreeVO)
     * * 核心重组逻辑：将数据库中的扁平结构转化为图结构的 VO 对象
     * 1. 分别从主表、节点表、连线表读取 PO 数据。
     * 2. 构建连线映射 Map：以起始节点 (From) 为 Key，聚合所有可能的出口线 (Line List)。
     * 3. 组装节点 Map：将节点元数据与其出口连线列表绑定，形成 RuleTreeNodeVO。
     * 4. 封装树根及全局拓扑，实现决策引擎的递归遍历基础。
     *
     * @param treeId 规则树标识
     * @return 完整组装的决策树 VO
     */
    @Override
    public RuleTreeVO queryRuleTreeVOByTreeId(String treeId) {
        // 1. 尝试从缓存获取已组装好的决策树镜像
        String cacheKey = Constants.RedisKey.RULE_TREE_VO_KEY + treeId;
        RuleTreeVO ruleTreeVOCache = redisService.getValue(cacheKey);
        if (null != ruleTreeVOCache) return ruleTreeVOCache;

        // 2. 数据库读取：树干、树叶（节点）、脉络（连线）
        RuleTree ruleTree = ruleTreeDao.queryRuleTreeByTreeId(treeId);
        List<RuleTreeNode> ruleTreeNodes = ruleTreeNodeDao.queryRuleTreeNodeListByTreeId(treeId);
        List<RuleTreeNodeLine> ruleTreeNodeLines = ruleTreeNodeLineDao.queryRuleTreeNodeLineListByTreeId(treeId);

        // 3. 建立连线索引：按起始节点分组连线
        Map<String, List<RuleTreeNodeLineVO>> treeNodeLineVOMap = new HashMap<>();
        for (RuleTreeNodeLine line : ruleTreeNodeLines) {
            RuleTreeNodeLineVO lineVO = RuleTreeNodeLineVO.builder().treeId(line.getTreeId())
                                                          .ruleNodeFrom(line.getRuleNodeFrom())
                                                          .ruleNodeTo(line.getRuleNodeTo())
                                                          .ruleLimitType(RuleLimitTypeVO.valueOf(line.getRuleLimitType()))
                                                          .ruleLimitValue(RuleLogicCheckTypeVO.valueOf(line.getRuleLimitValue()))
                                                          .build();
            treeNodeLineVOMap.computeIfAbsent(line.getRuleNodeFrom(), k -> new ArrayList<>()).add(lineVO);
        }

        // 4. 节点深度封装：关联节点属性与该节点的出口路径逻辑
        Map<String, RuleTreeNodeVO> treeNodeVOMap = new HashMap<>();
        for (RuleTreeNode node : ruleTreeNodes) {
            RuleTreeNodeVO nodeVO = RuleTreeNodeVO.builder().treeId(node.getTreeId())
                                                  .ruleKey(node.getRuleKey()).ruleDesc(node.getRuleDesc())
                                                  .ruleValue(node.getRuleValue())
                                                  .treeNodeLineVOList(treeNodeLineVOMap.get(node.getRuleKey()))
                                                  .build();
            // 存入 Map 时显式 trim，确保规则 Key 在比对时不受数据库空格影响
            treeNodeVOMap.put(node.getRuleKey().trim(), nodeVO);
        }

        // 5. 组装聚合对象 RuleTreeVO 并入库缓存
        RuleTreeVO ruleTreeVO = RuleTreeVO.builder().treeId(ruleTree.getTreeId())
                                          .treeName(ruleTree.getTreeName()).treeDesc(ruleTree.getTreeDesc())
                                          .treeRootRuleNode(ruleTree.getTreeRootRuleKey())
                                          .treeNodeMap(treeNodeVOMap).build();

        redisService.setValue(cacheKey, ruleTreeVO);
        return ruleTreeVO;
    }

    /**
     * 查询奖品关联的规则模型映射（如奖品是否绑定决策树 ID）
     */
    @Override
    public StrategyAwardRuleModelVO queryStrategyAwardRuleModel(Long strategyId, Integer awardId) {
        StrategyAward strategyAward = StrategyAward.builder().strategyId(strategyId).awardId(awardId).build();
        String ruleModel = strategyAwardDao.queryStrategyAwardRuleModel(strategyAward);
        if (StringUtils.isBlank(ruleModel)) return null;
        return StrategyAwardRuleModelVO.builder().ruleModels(ruleModel).build();
    }

    /**
     * 缓存奖品库存总量（初始化）
     *
     * @param cacheKey 缓存键 (strategy_award_count_key + strategyId + awardId)
     * @param awardCount 初始库存值
     */
    @Override
    public void cacheStrategyAwardCount(String cacheKey, Integer awardCount) {
        // 原子操作保障：若已存在则跳过，防止多节点部署时并发重置库存
        if (redisService.isExists(cacheKey)) return;
        // 使用 Redis AtomicLong 支撑高频原子增减逻辑
        redisService.setAtomicLong(cacheKey, awardCount);
    }

    /**
     * 奖品库存扣减算法（高并发核心逻辑）
     * * 实现机制：
     * 1. 预扣减 (decr)：利用 Redis 单线程原子递减初步削减流量。
     * 2. 状态锁 (setNx)：组合 "Key_库存余量" 作为分布式锁 Key。
     * 意图：确保针对同一个库存瞬时值，仅有一个请求能扣减成功，彻底杜绝高并发下的超卖现象。
     *
     * @param key 库存缓存键
     * @return boolean true 扣减成功，获得发奖资格
     */
    @Override
    public Boolean subtractAwardStock(String key) {
        // 1. 原子预减
        long surPlus = redisService.decr(key);
        if (surPlus < 0) {
            // 归零保护：当库存扣完，重置为0防止负值过大影响监控
            redisService.setValue(key, 0);
            return false;
        }

        // 2. 利用库存数值作为“状态快照锁”
        // 举例：剩余 5 件时，并发 10 个请求 decr 都会成功，但仅有一个请求能拿到 key_5 的 setNx 锁
        String lockKey = key + Constants.UNDERLINE + surPlus;
        Boolean lock = redisService.setNx(lockKey);
        if (!lock) {
            log.warn("奖品库存状态锁竞争失败，视为库存扣减冲突: {}", lockKey);
        }
        return lock;
    }

    /**
     * 将奖品库存消耗流水存入延迟队列
     * 目的：削峰填谷。通过 3 秒延迟，将 Redis 中的实时扣减操作平摊到数据库的异步同步中。
     *
     * @param strategyAwardStockKeyVO 包含策略 ID 与奖品 ID 的库存流水对象
     */
    @Override
    public void awardStockConsumeSendQueue(StrategyAwardStockKeyVO strategyAwardStockKeyVO) {
        String cacheKey = Constants.RedisKey.STRATEGY_AWARD_COUNT_QUERY_KEY;
        // 获取阻塞队列，衔接 Job 消费逻辑
        RBlockingQueue<StrategyAwardStockKeyVO> blockingQueue = redisService.getBlockingQueue(cacheKey);
        // 包装为延迟队列，平稳流量突刺
        RDelayedQueue<StrategyAwardStockKeyVO> delayedQueue = redisService.getDelayedQueue(blockingQueue);
        delayedQueue.offer(strategyAwardStockKeyVO, 3, TimeUnit.SECONDS);
    }

    /**
     * 从异步队列提取待处理的库存流水（供 Job 节点调用）
     */
    @Override
    public StrategyAwardStockKeyVO takeQueueValue() {
        String cacheKey = Constants.RedisKey.STRATEGY_AWARD_COUNT_QUERY_KEY;
        RBlockingQueue<StrategyAwardStockKeyVO> blockingQueue = redisService.getBlockingQueue(cacheKey);
        return blockingQueue.poll();
    }

    /**
     * 将 Redis 预扣减结果持久化同步至数据库
     */
    @Override
    public void updateStrategyAwardStock(Long strategyId, Integer awardId) {
        strategyAwardDao.updateStrategyAwardStock(StrategyAward.builder().strategyId(strategyId)
                                                               .awardId(awardId).build());
    }

    /**
     * 检索单个奖品实体信息（优先从缓存镜像中读取）
     */
    @Override
    public StrategyAwardEntity queryStrategyAwardEntity(Long strategyId, Integer awardId) {
        String cacheKey = Constants.RedisKey.STRATEGY_AWARD_KEY + strategyId + Constants.UNDERLINE + awardId;
        StrategyAwardEntity strategyAwardEntity = redisService.getValue(cacheKey);
        if (strategyAwardEntity != null) return strategyAwardEntity;

        StrategyAward strategyAward = strategyAwardDao.queryStrategyAwardEntity(StrategyAward.builder()
                                                                                             .strategyId(strategyId)
                                                                                             .awardId(awardId)
                                                                                             .build());
        strategyAwardEntity = StrategyAwardEntity.builder().strategyId(strategyAward.getStrategyId())
                                                 .awardId(strategyAward.getAwardId())
                                                 .awardCount(strategyAward.getAwardCount())
                                                 .awardCountSurplus(strategyAward.getAwardCountSurplus())
                                                 .awardRate(strategyAward.getAwardRate())
                                                 .sort(strategyAward.getSort())
                                                 .awardTitle(strategyAward.getAwardTitle())
                                                 .awardSubtitle(strategyAward.getAwardSubtitle()).build();
        redisService.setValue(cacheKey, strategyAwardEntity);
        return strategyAwardEntity;
    }
}