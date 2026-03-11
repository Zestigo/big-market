package com.c.infrastructure.adapter.repository;

import com.c.domain.strategy.model.entity.StrategyAwardEntity;
import com.c.domain.strategy.model.entity.StrategyEntity;
import com.c.domain.strategy.model.entity.StrategyRuleEntity;
import com.c.domain.strategy.model.vo.*;
import com.c.domain.strategy.repository.IStrategyRepository;
import com.c.domain.strategy.service.rule.chain.factory.DefaultChainFactory;
import com.c.infrastructure.dao.*;
import com.c.infrastructure.dao.po.*;
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
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 策略领域仓储实现类
 * 核心能力：1.领域与数据模型转换 2.策略数据缓存管理 3.奖品库存一致性维护 4.异步队列解耦
 *
 * @author cyh
 * @date 2026/03/11
 */
@Slf4j
@Repository
public class StrategyRepository implements IStrategyRepository {

    // ========== 依赖注入（按业务模块归类） ==========
    @Resource
    private IRedisService redisService;

    // 策略基础DAO - 策略/奖品/规则核心数据操作
    @Resource
    private IStrategyDao strategyDao;
    @Resource
    private IStrategyAwardDao strategyAwardDao;
    @Resource
    private IStrategyRuleDao strategyRuleDao;

    // 规则树DAO - 规则决策树相关数据操作
    @Resource
    private IRuleTreeDao ruleTreeDao;
    @Resource
    private IRuleTreeNodeDao ruleTreeNodeDao;
    @Resource
    private IRuleTreeNodeLineDao ruleTreeNodeLineDao;

    // 活动账户DAO - 用户抽奖次数/账户相关操作
    @Resource
    private IRaffleActivityDao raffleActivityDao;
    @Resource
    private IRaffleActivityAccountDao raffleActivityAccountDao;
    @Resource
    private IRaffleActivityAccountDayDao raffleActivityAccountDayDao;

    // ========== 策略基础查询 ==========

    /**
     * 查询策略主体信息（缓存懒加载）
     *
     * @param strategyId 策略ID
     * @return 策略实体，无数据返回null
     */
    @Override
    public StrategyEntity queryStrategyEntityByStrategyId(Long strategyId) {
        String cacheKey = Constants.RedisKey.STRATEGY_KEY + strategyId;
        StrategyEntity strategyEntity = redisService.getValue(cacheKey);
        if (Objects.nonNull(strategyEntity)) {
            return strategyEntity; // 缓存命中直接返回
        }

        // 缓存未命中，查询数据库
        Strategy strategy = strategyDao.queryStrategyEntityByStrategyId(strategyId);
        if (Objects.isNull(strategy)) {
            return null; // 无数据返回null
        }

        // PO转领域实体，做数据防腐
        strategyEntity = StrategyEntity
                .builder()
                .strategyId(strategy.getStrategyId())
                .strategyDesc(strategy.getStrategyDesc())
                .ruleModels(strategy.getRuleModels())
                .build();
        redisService.setValue(cacheKey, strategyEntity); // 回写缓存，懒加载

        return strategyEntity;
    }

    /**
     * 查询策略关联奖品列表（缓存懒加载）
     *
     * @param strategyId 策略ID
     * @return 策略奖品实体列表，无数据返回空列表
     */
    @Override
    public List<StrategyAwardEntity> queryStrategyAwardList(Long strategyId) {
        String cacheKey = Constants.RedisKey.STRATEGY_AWARD_LIST_KEY + strategyId;

        // 优先读缓存，减少数据库访问
        List<StrategyAwardEntity> strategyAwardEntities = redisService.getValue(cacheKey);
        if (Objects.nonNull(strategyAwardEntities) && !strategyAwardEntities.isEmpty()) {
            return strategyAwardEntities;
        }

        // 缓存未命中查库
        List<StrategyAward> strategyAwards = strategyAwardDao.queryStrategyAwardListByActivityId(strategyId);
        if (Objects.isNull(strategyAwards) || strategyAwards.isEmpty()) {
            return Collections.emptyList(); // 无数据返回空列表，避免NPE
        }

        // PO转Entity，领域层隔离数据层
        strategyAwardEntities = new ArrayList<>(strategyAwards.size());
        for (StrategyAward strategyAward : strategyAwards) {
            StrategyAwardEntity entity = StrategyAwardEntity
                    .builder()
                    .strategyId(strategyAward.getStrategyId())
                    .awardId(strategyAward.getAwardId())
                    .awardCount(strategyAward.getAwardCount())
                    .awardCountSurplus(strategyAward.getAwardCountSurplus())
                    .awardRate(strategyAward.getAwardRate())
                    .sort(strategyAward.getSort())
                    .awardTitle(strategyAward.getAwardTitle())
                    .awardSubtitle(strategyAward.getAwardSubtitle())
                    .ruleModels(strategyAward.getRuleModels())
                    .build();
            strategyAwardEntities.add(entity);
        }

        redisService.setValue(cacheKey, strategyAwardEntities); // 缓存预热

        return strategyAwardEntities;
    }

    /**
     * 查询单个奖品配置（缓存懒加载）
     *
     * @param strategyId 策略ID
     * @param awardId    奖品ID
     * @return 奖品实体，无数据返回null
     */
    @Override
    public StrategyAwardEntity queryStrategyAwardEntity(Long strategyId, Integer awardId) {
        String cacheKey = Constants.RedisKey.STRATEGY_AWARD_KEY + strategyId + Constants.UNDERLINE + awardId;
        StrategyAwardEntity strategyAwardEntity = redisService.getValue(cacheKey);
        if (Objects.nonNull(strategyAwardEntity)) {
            return strategyAwardEntity;
        }

        // 构造精准查询条件，避免全表扫描
        StrategyAward queryCondition = StrategyAward
                .builder()
                .strategyId(strategyId)
                .awardId(awardId)
                .build();
        StrategyAward strategyAward = strategyAwardDao.queryStrategyAward(queryCondition);
        if (Objects.isNull(strategyAward)) {
            return null;
        }

        // 转换为领域实体，只保留业务所需字段
        strategyAwardEntity = StrategyAwardEntity
                .builder()
                .strategyId(strategyAward.getStrategyId())
                .awardId(awardId)
                .awardCount(strategyAward.getAwardCount())
                .awardCountSurplus(strategyAward.getAwardCountSurplus())
                .awardRate(strategyAward.getAwardRate())
                .sort(strategyAward.getSort())
                .awardTitle(strategyAward.getAwardTitle())
                .awardSubtitle(strategyAward.getAwardSubtitle())
                .build();
        redisService.setValue(cacheKey, strategyAwardEntity);

        return strategyAwardEntity;
    }

    /**
     * 根据活动ID查询关联策略ID
     *
     * @param activityId 活动ID
     * @return 策略ID，无关联返回null
     */
    @Override
    public Long queryStrategyIdByActivityId(Long activityId) {
        return raffleActivityDao.queryStrategyIdByActivityId(activityId);
    }

    // ========== 策略规则相关 ==========

    /**
     * 查询策略规则配置值
     *
     * @param strategyId 策略ID
     * @param awardId    奖品ID（null表示查询通用规则）
     * @param ruleModel  规则模型标识
     * @return 规则配置内容，无配置返回null
     */
    @Override
    public String queryStrategyRuleValue(Long strategyId, Integer awardId, String ruleModel) {
        StrategyRule queryCondition = StrategyRule
                .builder()
                .strategyId(strategyId)
                .awardId(awardId)
                .ruleModel(ruleModel)
                .build();
        return strategyRuleDao.queryStrategyRuleValue(queryCondition);
    }

    /**
     * 查询策略规则配置值（通用查询）
     *
     * @param strategyId 策略ID
     * @param ruleModel  规则模型标识
     * @return 规则配置内容，无配置返回null
     */
    @Override
    public String queryStrategyRuleValue(Long strategyId, String ruleModel) {
        return queryStrategyRuleValue(strategyId, null, ruleModel); // 复用重载方法，减少冗余
    }

    /**
     * 查询策略规则完整实体
     *
     * @param strategyId 策略ID
     * @param ruleModel  规则模型标识
     * @return 策略规则实体，无数据返回null
     */
    @Override
    public StrategyRuleEntity queryStrategyRule(Long strategyId, String ruleModel) {
        StrategyRule queryCondition = StrategyRule
                .builder()
                .strategyId(strategyId)
                .ruleModel(ruleModel)
                .build();

        StrategyRule strategyRule = strategyRuleDao.queryStrategyRule(queryCondition);
        if (Objects.isNull(strategyRule)) {
            return null;
        }

        // 数据模型转领域模型，隔离底层数据结构
        return StrategyRuleEntity
                .builder()
                .strategyId(strategyRule.getStrategyId())
                .awardId(strategyRule.getAwardId())
                .ruleType(strategyRule.getRuleType())
                .ruleModel(strategyRule.getRuleModel())
                .ruleValue(strategyRule.getRuleValue())
                .ruleDesc(strategyRule.getRuleDesc())
                .build();
    }

    /**
     * 构建立体化规则决策树模型
     *
     * @param treeId 规则树ID
     * @return 规则决策树VO，数据不完整返回null
     */
    @Override
    public RuleTreeVO queryRuleTreeVOByTreeId(String treeId) {
        String cacheKey = Constants.RedisKey.RULE_TREE_VO_KEY + treeId;
        RuleTreeVO ruleTreeVO = redisService.getValue(cacheKey);
        if (Objects.nonNull(ruleTreeVO)) {
            return ruleTreeVO; // 缓存命中直接返回
        }

        // 查库获取扁平数据，后续组装为结构化树
        RuleTree ruleTree = ruleTreeDao.queryRuleTreeByTreeId(treeId);
        List<RuleTreeNode> ruleTreeNodes = ruleTreeNodeDao.queryRuleTreeNodeListByTreeId(treeId);
        List<RuleTreeNodeLine> ruleTreeNodeLines = ruleTreeNodeLineDao.queryRuleTreeNodeLineListByTreeId(treeId);

        // 数据完整性校验，任一数据缺失则返回null
        if (Objects.isNull(ruleTree) || Objects.isNull(ruleTreeNodes) || Objects.isNull(ruleTreeNodeLines)) {
            log.warn("规则决策树数据不完整！treeId: {}", treeId);
            return null;
        }

        // 构建连线索引，按起始节点分组，方便后续组装
        Map<String, List<RuleTreeNodeLineVO>> nodeLineVOMap = new HashMap<>();
        for (RuleTreeNodeLine line : ruleTreeNodeLines) {
            RuleTreeNodeLineVO lineVO = RuleTreeNodeLineVO
                    .builder()
                    .treeId(line.getTreeId())
                    .ruleNodeFrom(line.getRuleNodeFrom())
                    .ruleNodeTo(line.getRuleNodeTo())
                    .ruleLimitType(RuleLimitTypeVO.valueOf(line.getRuleLimitType()))
                    .ruleLimitValue(RuleLogicCheckTypeVO.valueOf(line.getRuleLimitValue()))
                    .build();
            nodeLineVOMap
                    .computeIfAbsent(line.getRuleNodeFrom(), k -> new ArrayList<>())
                    .add(lineVO);
        }

        // 构建节点映射，快速查找节点信息
        Map<String, RuleTreeNodeVO> nodeVOMap = new HashMap<>();
        for (RuleTreeNode node : ruleTreeNodes) {
            String ruleKey = node
                    .getRuleKey()
                    .trim(); // 去除空格，避免匹配失败
            RuleTreeNodeVO nodeVO = RuleTreeNodeVO
                    .builder()
                    .treeId(node.getTreeId())
                    .ruleKey(ruleKey)
                    .ruleDesc(node.getRuleDesc())
                    .ruleValue(node.getRuleValue())
                    .treeNodeLineVOList(nodeLineVOMap.get(ruleKey)) // 关联节点连线
                    .build();
            nodeVOMap.put(ruleKey, nodeVO);
        }

        // 组装决策树并回写缓存
        ruleTreeVO = RuleTreeVO
                .builder()
                .treeId(ruleTree.getTreeId())
                .treeName(ruleTree.getTreeName())
                .treeDesc(ruleTree.getTreeDesc())
                .treeRootRuleNode(ruleTree.getTreeRootRuleKey())
                .treeNodeMap(nodeVOMap)
                .build();
        redisService.setValue(cacheKey, ruleTreeVO);

        return ruleTreeVO;
    }

    /**
     * 查询奖品关联的规则模型
     *
     * @param strategyId 策略ID
     * @param awardId    奖品ID
     * @return 奖品规则模型VO，无绑定返回null
     */
    @Override
    public StrategyAwardRuleModelVO queryStrategyAwardRuleModel(Long strategyId, Integer awardId) {
        StrategyAward queryCondition = StrategyAward
                .builder()
                .strategyId(strategyId)
                .awardId(awardId)
                .build();

        String ruleModel = strategyAwardDao.queryStrategyAwardRuleModel(queryCondition);
        if (StringUtils.isBlank(ruleModel)) {
            return null; // 无规则模型返回null
        }

        return StrategyAwardRuleModelVO
                .builder()
                .ruleModels(ruleModel)
                .build();
    }

    /**
     * 查询奖品规则锁定次数要求
     *
     * @param treeIds 规则树ID数组
     * @return 树ID-解锁次数映射
     */
    @Override
    public Map<String, Integer> queryAwardRuleLockCount(String[] treeIds) {
        if (Objects.isNull(treeIds) || treeIds.length == 0) {
            return Collections.emptyMap(); // 空参数返回空Map，避免NPE
        }

        List<RuleTreeNode> ruleTreeNodes = ruleTreeNodeDao.queryRuleLocks(treeIds);
        if (Objects.isNull(ruleTreeNodes) || ruleTreeNodes.isEmpty()) {
            log.warn("未查询到规则锁定配置！treeIds: {}", Arrays.toString(treeIds));
            return Collections.emptyMap();
        }

        // 转换为树ID-解锁次数映射，重复ID取最新值
        return ruleTreeNodes
                .stream()
                .collect(Collectors.toMap(RuleTreeNode::getTreeId, node -> {
                    try {
                        String ruleValue = node.getRuleValue();
                        return StringUtils.isNotBlank(ruleValue) ? Integer.parseInt(ruleValue.trim()) : 0;
                    } catch (NumberFormatException e) {
                        log.error("规则锁定次数解析失败！treeId: {}, ruleValue: {}", node.getTreeId(), node.getRuleValue(), e);
                        return 0; // 解析失败默认返回0次
                    }
                }, (existingValue, newValue) -> newValue));
    }

    /**
     * 查询并组装权重规则配置
     *
     * @param strategyId 策略ID
     * @return 权重规则配置列表
     */
    public List<RuleWeightVO> queryAwardRuleWeight(Long strategyId) {
        String cacheKey = Constants.RedisKey.STRATEGY_RULE_WEIGHT_KEY + strategyId;
        List<RuleWeightVO> ruleWeightVOS = redisService.getValue(cacheKey);
        if (null != ruleWeightVOS) return ruleWeightVOS; // 缓存命中直接返回

        // 查询权重规则原始配置
        String ruleModel = DefaultChainFactory.LogicModel.RULE_WEIGHT.getCode();
        StrategyRule strategyRule = StrategyRule
                .builder()
                .strategyId(strategyId)
                .ruleModel(ruleModel)
                .build();
        String ruleValue = strategyRuleDao.queryStrategyRuleValue(strategyRule);

        if (StringUtils.isBlank(ruleValue)) return new ArrayList<>(); // 无配置返回空列表

        // 解析规则值为结构化数据
        StrategyRuleEntity strategyRuleEntity = StrategyRuleEntity
                .builder()
                .ruleModel(ruleModel)
                .ruleValue(ruleValue)
                .build();
        Map<String, List<Integer>> ruleWeightValues = strategyRuleEntity.getRuleValueGroup();

        // 批量查询奖品信息，减少数据库交互
        List<Integer> allAwardIds = ruleWeightValues
                .values()
                .stream()
                .flatMap(Collection::stream)
                .distinct()
                .collect(Collectors.toList());
        List<StrategyAward> strategyAwards = strategyAwardDao.queryStrategyAwardListByAwardIds(strategyId, allAwardIds);
        Map<Integer, StrategyAward> awardMap = strategyAwards
                .stream()
                .collect(Collectors.toMap(StrategyAward::getAwardId, award -> award));

        // 组装权重规则VO，关联奖品信息
        ruleWeightVOS = new ArrayList<>();
        for (Map.Entry<String, List<Integer>> entry : ruleWeightValues.entrySet()) {
            String weightKey = entry.getKey();
            List<Integer> awardIds = entry.getValue();
            List<RuleWeightVO.Award> awardList = new ArrayList<>();

            for (Integer awardId : awardIds) {
                StrategyAward strategyAward = awardMap.get(awardId);
                if (null != strategyAward) {
                    awardList.add(RuleWeightVO.Award
                            .builder()
                            .awardId(strategyAward.getAwardId())
                            .awardTitle(strategyAward.getAwardTitle())
                            .build());
                }
            }

            ruleWeightVOS.add(RuleWeightVO
                    .builder()
                    .ruleValue(ruleValue)
                    .weight(Integer.valueOf(weightKey))
                    .awardIds(awardIds)
                    .awardList(awardList)
                    .build());
        }

        redisService.setValue(cacheKey, ruleWeightVOS); // 缓存预热

        return ruleWeightVOS;
    }

    /**
     * 缓存策略装配算法名称
     *
     * @param key      缓存键前缀
     * @param beanName 算法Bean名称
     */
    @Override
    public void cacheStrategyArmoryAlgorithm(String key, String beanName) {
        String cacheKey = Constants.RedisKey.STRATEGY_ARMORY_ALGORITHM_KEY + key;
        redisService.setValue(cacheKey, beanName);
    }

    /**
     * 从缓存查询策略装配算法名称
     *
     * @param key 缓存键前缀
     * @return 算法Bean名称，无数据返回null
     */
    @Override
    public String queryStrategyArmoryAlgorithmFromCache(String key) {
        String cacheKey = Constants.RedisKey.STRATEGY_ARMORY_ALGORITHM_KEY + key;
        if (!redisService.isExists(cacheKey)) return null; // 缓存不存在直接返回null
        return redisService.getValue(cacheKey);
    }

    // ========== 抽奖概率相关 ==========

    /**
     * 存储抽奖概率查找映射表（O(1)查询）
     *
     * @param key        策略装配唯一标识
     * @param rateRange  概率分母
     * @param shuffleMap 随机数-奖品ID映射
     */
    @Override
    public void storeStrategyAwardSearchRateTable(String key, Integer rateRange, Map<Integer, Integer> shuffleMap) {
        // 1. 存储概率分母
        String rateRangeCacheKey = Constants.RedisKey.STRATEGY_RATE_RANGE_KEY + key;
        redisService.setValue(rateRangeCacheKey, rateRange);

        // 2. 存储O(1)概率映射表，先清旧数据防污染
        String tableKey = Constants.RedisKey.STRATEGY_RATE_TABLE_O1_KEY + key;
        Map<Integer, Integer> cacheMap = redisService.getMap(tableKey);
        cacheMap.clear();
        cacheMap.putAll(shuffleMap);
    }

    /**
     * 存储区间概率查找表（O(LogN)算法）
     *
     * @param key       策略装配唯一标识
     * @param rateRange 总概率量程
     * @param rangeMap  区间上限-奖品ID映射
     */
    @Override
    public void storeStrategyAwardSearchRateTable(String key, Integer rateRange,
                                                  NavigableMap<Integer, Integer> rangeMap) {
        // 存储概率分母
        String rateRangeCacheKey = Constants.RedisKey.STRATEGY_RATE_RANGE_KEY + key;
        redisService.setValue(rateRangeCacheKey, rateRange);

        // 存储区间映射表，Redisson自动还原TreeMap结构
        String rateTableCacheKey = Constants.RedisKey.STRATEGY_RATE_TABLE_OLN_KEY + key;
        redisService.setValue(rateTableCacheKey, rangeMap);

        log.info("OLogN 概率表已持久化至 Redis Key:{} 规模:{}", rateTableCacheKey, rangeMap.size());
    }

    /**
     * 获取抽奖概率分母
     *
     * @param key 策略装配唯一标识
     * @return 概率分母
     * @throws AppException 策略未装配异常
     */
    @Override
    public int getRateRange(String key) {
        String cacheKey = Constants.RedisKey.STRATEGY_RATE_RANGE_KEY + key;

        if (!redisService.isExists(cacheKey)) {
            log.error("策略概率范围缓存不存在！key: {}", key);
            throw new AppException(ResponseCode.UN_ASSEMBLED_STRATEGY_ARMORY);
        }

        return redisService.getValue(cacheKey);
    }

    /**
     * 获取预排序的区间查找表（O(LogN)）
     *
     * @param key 策略装配Key
     * @return 有序区间映射表
     * @throws AppException 概率表丢失异常
     */
    @Override
    public NavigableMap<Integer, Integer> getRangeMap(String key) {
        String cacheKey = Constants.RedisKey.STRATEGY_RATE_TABLE_OLN_KEY + key;

        // 1. 先用 Object 接收，避开泛型的“类型欺骗”
        Map<Object, Object> rawMap = redisService.getValue(cacheKey);

        if (null == rawMap || rawMap.isEmpty()) {
            log.error("OLogN 概率表丢失或格式错误！Key: {}", key);
            throw new AppException(ResponseCode.UN_ASSEMBLED_STRATEGY_ARMORY);
        }

        // 2.关键：重建具备红黑树结构的 TreeMap，并强制转换 Key 为 Integer
        NavigableMap<Integer, Integer> rangeMap = new TreeMap<>();
        try {
            for (Map.Entry<Object, Object> entry : rawMap.entrySet()) {
                // entry.getKey().toString() 拿到字符串 "100"，再解析回 Integer 100
                Integer k = Integer.valueOf(entry
                        .getKey()
                        .toString());
                Integer v = Integer.valueOf(entry
                        .getValue()
                        .toString());
                rangeMap.put(k, v);
            }
        } catch (Exception e) {
            log.error("OLogN 概率表数据转换异常！Key: {}, 原始对象类型: {}", key, rawMap
                    .getClass()
                    .getName(), e);
            throw new AppException(ResponseCode.UN_ERROR, "概率表数据解析失败");
        }

        return rangeMap;
    }

    /**
     * 根据随机数获取中奖奖品ID（O(1)）
     *
     * @param key     策略装配唯一标识
     * @param rateKey 随机索引值
     * @return 中奖奖品ID
     */
    @Override
    public Integer getStrategyAwardAssemble(String key, int rateKey) {
        String cacheKey = Constants.RedisKey.STRATEGY_RATE_TABLE_O1_KEY + key;
        return redisService.getFromMap(cacheKey, rateKey);
    }

    // ========== 库存管理 ==========

    /**
     * 初始化缓存奖品库存（原子操作）
     *
     * @param cacheKey   库存缓存键
     * @param awardCount 奖品初始库存
     */
    @Override
    public void cacheStrategyAwardCount(String cacheKey, Integer awardCount) {
        if (redisService.isExists(cacheKey)) {
            return; // 已初始化则跳过，避免覆盖
        }
        redisService.setAtomicLong(cacheKey, awardCount); // 原子设置，防止并发问题
    }

    /**
     * 奖品库存扣减（防超卖）
     *
     * @param key         库存缓存键
     * @param endDateTime 活动结束时间
     * @return true=扣减成功 false=失败
     * @throws AppException 活动过期/库存不足异常
     */
    @Override
    public Boolean subtractAwardStock(String key, Date endDateTime) {
        // 1. 原子扣减，保证库存操作一致性
        long surplus = redisService.decr(key);
        if (surplus < 0) {
            redisService.incr(key); // 库存不足，回补计数器
            throw new AppException(ResponseCode.STRATEGY_AWARD_STOCK_EMPTY);
        }

        // 2. 构建唯一锁，实现幂等防超卖
        String lockKey = key + Constants.UNDERLINE + surplus;
        long expire = (endDateTime != null) ? (endDateTime.getTime() - System.currentTimeMillis()) / 1000 : 86400;

        return redisService.setNx(lockKey, Math.max(1, expire), TimeUnit.SECONDS); // 过期时间至少1秒
    }

    /**
     * 奖品库存扣减（默认锁配置）
     *
     * @param key 库存缓存键
     * @return true=扣减成功 false=失败
     */
    @Override
    public Boolean subtractAwardStock(String key) {
        return subtractAwardStock(key, null); // 复用重载方法，默认活动有效期1天
    }

    /**
     * 同步更新数据库奖品库存
     *
     * @param strategyId 策略ID
     * @param awardId    奖品ID
     */
    @Override
    public void updateStrategyAwardStock(Long strategyId, Integer awardId) {
        StrategyAward updateCondition = StrategyAward
                .builder()
                .strategyId(strategyId)
                .awardId(awardId)
                .build();
        strategyAwardDao.updateStrategyAwardStock(updateCondition); // 乐观锁更新库存
    }

    // ========== 异步队列相关 ==========

    /**
     * 库存扣减流水入延迟队列
     *
     * @param strategyAwardStockKeyVO 库存流水对象
     */
    @Override
    public void awardStockConsumeSendQueue(StrategyAwardStockKeyVO strategyAwardStockKeyVO) {
        String queueCacheKey = Constants.RedisKey.STRATEGY_AWARD_COUNT_QUERY_KEY;
        RBlockingQueue<StrategyAwardStockKeyVO> blockingQueue = redisService.getBlockingQueue(queueCacheKey);
        RDelayedQueue<StrategyAwardStockKeyVO> delayedQueue = redisService.getDelayedQueue(blockingQueue);

        // 延迟3秒入队，避免缓存与数据库数据不一致
        delayedQueue.offer(strategyAwardStockKeyVO, 3, TimeUnit.SECONDS);
    }

    /**
     * 从阻塞队列获取库存扣减流水
     *
     * @return 库存流水对象（null=队列为空）
     */
    @Override
    public StrategyAwardStockKeyVO takeQueueValue() {
        String queueCacheKey = Constants.RedisKey.STRATEGY_AWARD_COUNT_QUERY_KEY;
        RBlockingQueue<StrategyAwardStockKeyVO> blockingQueue = redisService.getBlockingQueue(queueCacheKey);
        return blockingQueue.poll(); // 非阻塞获取，队列为空返回null
    }

    // ========== 用户抽奖次数查询 ==========

    /**
     * 查询用户今日抽奖次数
     *
     * @param userId     用户ID
     * @param strategyId 策略ID
     * @return 今日已抽奖次数
     */
    @Override
    public Integer queryTodayUserRaffleCount(String userId, Long strategyId) {
        // 先通过策略ID查关联活动ID
        Long activityId = raffleActivityDao.queryActivityIdByStrategyId(strategyId);
        if (Objects.isNull(activityId)) {
            log.warn("策略无关联活动！strategyId: {}", strategyId);
            return 0;
        }

        // 构造今日账户查询条件
        RaffleActivityAccountDay queryCondition = RaffleActivityAccountDay
                .builder()
                .userId(userId)
                .activityId(activityId)
                .build();
        queryCondition.setDay(RaffleActivityAccountDay.currentDay()); // 设置今日日期

        RaffleActivityAccountDay userAccountDay = raffleActivityAccountDayDao.queryActivityAccountDay(queryCondition);
        if (Objects.isNull(userAccountDay)) {
            return 0; // 今日无抽奖记录返回0
        }

        // 已抽奖次数 = 总次数 - 剩余次数
        return userAccountDay.getDayCount() - userAccountDay.getDayCountSurplus();
    }

    /**
     * 查询用户累计抽奖次数
     *
     * @param userId     用户ID
     * @param strategyId 策略ID
     * @return 累计已抽奖次数
     */
    @Override
    public Integer queryTotalUserRaffleCount(String userId, Long strategyId) {
        // 先通过策略ID查关联活动ID
        Long activityId = raffleActivityDao.queryActivityIdByStrategyId(strategyId);
        if (Objects.isNull(activityId)) {
            log.warn("策略未关联活动！strategyId: {}", strategyId);
            return 0;
        }

        // 构造累计账户查询条件
        RaffleActivityAccount queryCondition = RaffleActivityAccount
                .builder()
                .userId(userId)
                .activityId(activityId)
                .build();

        RaffleActivityAccount userAccount = raffleActivityAccountDao.queryTotalUserRaffleCount(queryCondition);
        if (Objects.isNull(userAccount)) {
            log.info("用户活动账户不存在！userId: {} activityId: {}", userId, activityId);
            return 0; // 无账户记录返回0
        }

        // 累计已抽奖次数 = 总次数 - 剩余次数
        return userAccount.getTotalCount() - userAccount.getTotalCountSurplus();
    }

}