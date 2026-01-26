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
 * 策略领域仓储实现服务
 * 职责：负责策略相关数据的持久化、缓存管理及领域模型与数据库对象的转换
 * * @author cyh
 *
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

    @Override
    public List<StrategyAwardEntity> queryStrategyAwardList(Long strategyId) {
        // 构建缓存Key，用于快速获取该策略下的所有奖品配置
        String cacheKey = Constants.RedisKey.STRATEGY_AWARD_LIST_KEY + strategyId;

        // 步骤1：尝试从 Redis 获取缓存数据，减少数据库负载
        List<StrategyAwardEntity> strategyAwardEntities = redisService.getValue(cacheKey);
        if (null != strategyAwardEntities && !strategyAwardEntities.isEmpty()) {
            return strategyAwardEntities;
        }

        // 步骤2：缓存未命中，从数据库查询持久化对象 (PO)
        List<StrategyAward> strategyAwards = strategyAwardDao.queryStrategyAwardListByStrategyId(strategyId);

        // 步骤3：将数据库 PO 转换为领域层 Entity。
        // 这样做是为了防止底层表结构变化直接影响业务逻辑，实现层级隔离。
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

        // 步骤4：将转换后的实体列表写回 Redis，供下次查询直接使用
        redisService.setValue(cacheKey, strategyAwardEntities);

        return strategyAwardEntities;
    }

    @Override
    public void storeStrategyAwardSearchRateTable(String key, Integer rateRange,
                                                  Map<Integer, Integer> shuffleStrategyAwardSearchRateTables) {
        // 1. 存储本策略抽奖时随机数的上限（范围值），如 10000 对应万分位精度
        redisService.setValue(Constants.RedisKey.STRATEGY_RATE_RANGE_KEY + key, rateRange);

        // 2. 获取 Redis 中的 Hash 结构映射表
        // 这一步是为了将打乱后的奖品分布 [随机位 -> 奖品ID] 存入 Redis Hash。
        // 使用 Hash 结构可以实现在抽奖时通过随机数直接 O(1) 定位奖品。
        Map<Integer, Integer> cacheRateTable =
                redisService.getMap(Constants.RedisKey.STRATEGY_RATE_TABLE_KEY + key);
        cacheRateTable.putAll(shuffleStrategyAwardSearchRateTables);
    }

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

    @Override
    public Integer getStrategyAwardAssemble(String key, int rateKey) {
        // 直接根据随机数 rateKey 从 Redis Hash 映射表中通过 Key 快速获取奖品 ID
        return redisService.getFromMap(Constants.RedisKey.STRATEGY_RATE_TABLE_KEY + key, rateKey);
    }

    @Override
    public String queryStrategyRuleValue(Long strategyId, Integer awardId, String ruleModel) {
        // 构造查询条件对象，支持策略级别和奖品级别规则的统一查询
        StrategyRule strategyRule = StrategyRule.builder().strategyId(strategyId).awardId(awardId)
                                                .ruleModel(ruleModel).build();
        return strategyRuleDao.queryStrategyRuleValue(strategyRule);
    }

    @Override
    public String queryStrategyRuleValue(Long strategyId, String ruleModel) {
        // 重载方法：当不针对特定奖品时，awardId 传 null
        return queryStrategyRuleValue(strategyId, null, ruleModel);
    }

    @Override
    public StrategyEntity queryStrategyEntityByStrategyId(Long strategyId) {
        String cacheKey = Constants.RedisKey.STRATEGY_STRATEGY_KEY + strategyId;
        // 1. 优先从缓存获取策略主体配置
        StrategyEntity strategyEntity = redisService.getValue(cacheKey);
        if (null != strategyEntity) return strategyEntity;

        // 2. 缓存缺失，查询数据库
        Strategy strategy = strategyDao.queryStrategyEntityByStrategyId(strategyId);
        if (null == strategy) return null;

        // 3. 将 PO 转换为领域 Entity，并映射其规则模型列表
        strategyEntity = StrategyEntity.builder().strategyId(strategy.getStrategyId())
                                       .strategyDesc(strategy.getStrategyDesc())
                                       .ruleModels(strategy.getRuleModels()).build();

        // 4. 写回缓存，防止频繁击穿数据库
        redisService.setValue(cacheKey, strategyEntity);

        return strategyEntity;
    }

    @Override
    public StrategyRuleEntity queryStrategyRule(Long strategyId, String ruleModel) {
        // 构建数据库查询请求对象
        StrategyRule strategyRuleReq = StrategyRule.builder().strategyId(strategyId).ruleModel(ruleModel)
                                                   .build();

        // 执行 DAO 查询获取规则原始记录
        StrategyRule strategyRuleRes = strategyRuleDao.queryStrategyRule(strategyRuleReq);
        if (null == strategyRuleRes) return null;

        // 组装领域实体：包含规则类型、配置值、描述等核心逻辑属性
        return StrategyRuleEntity.builder().strategyId(strategyRuleRes.getStrategyId())
                                 .awardId(strategyRuleRes.getAwardId())
                                 .ruleType(strategyRuleRes.getRuleType())
                                 .ruleModel(strategyRuleRes.getRuleModel())
                                 .ruleValue(strategyRuleRes.getRuleValue())
                                 .ruleDesc(strategyRuleRes.getRuleDesc()).build();
    }

    @Override
    public RuleTreeVO queryRuleTreeVOByTreeId(String treeId) {
        // 1. 尝试命中规则树 VO 缓存，减少复杂的聚合查询
        String cacheKey = Constants.RedisKey.RULE_TREE_VO_KEY + treeId;
        RuleTreeVO ruleTreeVOCache = redisService.getValue(cacheKey);
        if (null != ruleTreeVOCache) return ruleTreeVOCache;

        // 2. 数据库联查：分别获取树根信息、所有节点信息、所有连线逻辑
        RuleTree ruleTree = ruleTreeDao.queryRuleTreeByTreeId(treeId);
        List<RuleTreeNode> ruleTreeNodes = ruleTreeNodeDao.queryRuleTreeNodeListByTreeId(treeId);
        List<RuleTreeNodeLine> ruleTreeNodeLines =
                ruleTreeNodeLineDao.queryRuleTreeNodeLineListByTreeId(treeId);

        // 3. 核心步骤：构建节点间的“连线”映射 Map。key 是起始节点，value 是从该节点出发的所有连线。
        Map<String, List<RuleTreeNodeLineVO>> treeNodeLineVOMap = new HashMap<>();
        for (RuleTreeNodeLine line : ruleTreeNodeLines) {
            RuleTreeNodeLineVO lineVO = RuleTreeNodeLineVO.builder().treeId(line.getTreeId())
                                                          .ruleNodeFrom(line.getRuleNodeFrom())
                                                          .ruleNodeTo(line.getRuleNodeTo())
                                                          .ruleLimitType(RuleLimitTypeVO.valueOf(line.getRuleLimitType()))
                                                          .ruleLimitValue(RuleLogicCheckTypeVO.valueOf(line.getRuleLimitValue()))
                                                          .build();
            // 将连线按起始节点归类，方便后续节点执行时直接找到下一跳
            treeNodeLineVOMap.computeIfAbsent(line.getRuleNodeFrom(), k -> new ArrayList<>()).add(lineVO);
        }

        // 4. 核心步骤：构建“节点”映射 Map。将每一个节点及其对应的出口连线逻辑组装在一起。
        Map<String, RuleTreeNodeVO> treeNodeVOMap = new HashMap<>();
        for (RuleTreeNode node : ruleTreeNodes) {
            RuleTreeNodeVO nodeVO = RuleTreeNodeVO.builder().treeId(node.getTreeId())
                                                  .ruleKey(node.getRuleKey()).ruleDesc(node.getRuleDesc())
                                                  .ruleValue(node.getRuleValue())
                                                  .treeNodeLineVOList(treeNodeLineVOMap.get(node.getRuleKey()))
                                                  .build();
            // 存入 Map 时显式 trim，防止数据库空格导致 Key 匹配失效
            treeNodeVOMap.put(node.getRuleKey().trim(), nodeVO);
        }

        // 5. 组装整棵决策树，并存入缓存
        RuleTreeVO ruleTreeVO = RuleTreeVO.builder().treeId(ruleTree.getTreeId())
                                          .treeName(ruleTree.getTreeName()).treeDesc(ruleTree.getTreeDesc())
                                          .treeRootRuleNode(ruleTree.getTreeRootRuleKey())
                                          .treeNodeMap(treeNodeVOMap).build();

        redisService.setValue(cacheKey, ruleTreeVO);
        return ruleTreeVO;
    }

    @Override
    public StrategyAwardRuleModelVO queryStrategyAwardRuleModel(Long strategyId, Integer awardId) {
        // 构建请求对象查询奖品关联的规则模型（如抽奖N次必中、库存锁等）
        StrategyAward strategyAward = StrategyAward.builder().strategyId(strategyId).awardId(awardId).build();
        String ruleModel = strategyAwardDao.queryStrategyAwardRuleModel(strategyAward);
        if (StringUtils.isBlank(ruleModel)) return null;
        // 封装为领域 VO 对象返回
        return StrategyAwardRuleModelVO.builder().ruleModels(ruleModel).build();
    }

    @Override
    public void cacheStrategyAwardCount(String cacheKey, Integer awardCount) {
        // 若缓存中已存在该库存，则不再重复设置，保证初始化的原子性
        if (redisService.isExists(cacheKey)) return;
        // 使用 Redis 的 AtomicLong 记录原子库存，支撑高并发下的库存扣减
        redisService.setAtomicLong(cacheKey, awardCount);
    }

    @Override
    public Boolean subtractAwardStock(String key) {
        // 1. 使用 Redis 的原子递减操作进行预减库存
        long surPlus = redisService.decr(key);
        // 2. 如果剩余库存小于0，表示库存已经扣完，返回失败
        if (surPlus < 0) {
            // 修正 Redis 中的负数值，归零处理
            redisService.setValue(key, 0);
            return false;
        }

        // 3. 构建分布式锁 Key，由 "库存Key_剩余库存值" 组成
        // 这样做是为了在高并发下，通过 setNx 确保对于同一个剩余值只能由一个请求扣减成功，防止超卖。
        String lockKey = key + Constants.UNDERLINE + surPlus;
        Boolean lock = redisService.setNx(lockKey);
        if (!lock) {
            log.warn("策略奖品库存加锁失败 {}", lockKey);
        }
        return lock;
    }

    @Override
    public void awardStockConsumeSendQueue(StrategyAwardStockKeyVO strategyAwardStockKeyVO) {
        // 定义库存消耗队列的 Key
        String cacheKey = Constants.RedisKey.STRATEGY_AWARD_COUNT_QUEUE_KEY;
        // 使用 Redisson 的阻塞队列获取任务
        RBlockingQueue<StrategyAwardStockKeyVO> blockingQueue = redisService.getBlockingQueue(cacheKey);
        // 通过延迟队列包装，实现在 3 秒后再将扣减任务放入消费队列，平滑数据库写入压力
        RDelayedQueue<StrategyAwardStockKeyVO> delayedQueue = redisService.getDelayedQueue(blockingQueue);
        delayedQueue.offer(strategyAwardStockKeyVO, 3, TimeUnit.SECONDS);
    }

    @Override
    public StrategyAwardStockKeyVO takeQueueValue() {
        // 从 Redis 阻塞队列中弹出一个待处理的库存扣减任务
        String cacheKey = Constants.RedisKey.STRATEGY_AWARD_COUNT_QUEUE_KEY;
        RBlockingQueue<StrategyAwardStockKeyVO> blockingQueue = redisService.getBlockingQueue(cacheKey);
        return blockingQueue.poll();
    }

    @Override
    public void updateStrategyAwardStock(Long strategyId, Integer awardId) {
        // 最终调用数据库 DAO，将 Redis 中成功预扣减的库存反映到 MySQL 持久层
        strategyAwardDao.updateStrategyAwardStock(StrategyAward.builder().strategyId(strategyId)
                                                               .awardId(awardId).build());
    }

    @Override
    public StrategyAwardEntity queryStrategyAwardEntity(Long strategyId, Integer awardId) {
        // 定义库存消耗队列的 Key
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