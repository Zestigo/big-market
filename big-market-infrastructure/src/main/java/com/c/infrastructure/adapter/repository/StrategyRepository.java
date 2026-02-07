package com.c.infrastructure.adapter.repository;

import com.c.domain.strategy.model.entity.StrategyAwardEntity;
import com.c.domain.strategy.model.entity.StrategyEntity;
import com.c.domain.strategy.model.entity.StrategyRuleEntity;
import com.c.domain.strategy.model.vo.*;
import com.c.domain.strategy.repository.IStrategyRepository;
import com.c.domain.strategy.service.rule.chain.factory.DefaultChainFactory;
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
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 策略领域仓储实现类
 * <p>
 * 核心职责：
 * 1.  数据防腐与转换：屏蔽 MySQL/Redis 技术细节，完成 PO（持久化对象）与 Entity（领域实体）的映射转换。
 * 2.  缓存管理：实现策略、奖品、规则等数据的缓存懒加载与刷新，支撑高并发场景下的读取性能。
 * 3.  状态维护：维护抽奖概率查找表、奖品库存（Redis 原子操作），保障高并发下的数据一致性。
 * 4.  异步解耦：将库存扣减流水放入延迟队列，实现 Redis 预扣减与 DB 最终一致性的异步同步。
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
    @Resource
    private IRaffleActivityDao raffleActivityDao;
    @Resource
    private IRaffleActivityAccountDao raffleActivityAccountDao;
    @Resource
    private IRaffleActivityAccountDayDao raffleActivityAccountDayDao;

    /**
     * 查询策略关联的奖品列表（带缓存懒加载）
     * 流程：Redis 缓存优先 -> 缓存未命中则查询 DB -> PO 转 Entity -> 回写缓存保障后续性能
     *
     * @param strategyId 策略ID
     * @return 策略关联的奖品领域实体列表
     */
    @Override
    public List<StrategyAwardEntity> queryStrategyAwardList(Long strategyId) {
        // 1. 构建缓存键（策略奖品列表缓存）
        String cacheKey = Constants.RedisKey.STRATEGY_AWARD_LIST_KEY + strategyId;

        // 2. 优先从 Redis 读取缓存，直接返回已序列化的领域实体
        List<StrategyAwardEntity> strategyAwardEntities = redisService.getValue(cacheKey);
        if (Objects.nonNull(strategyAwardEntities) && !strategyAwardEntities.isEmpty()) {
            return strategyAwardEntities;
        }

        // 3. 缓存未命中，从数据库查询策略奖品 PO 列表
        List<StrategyAward> strategyAwards = strategyAwardDao.queryStrategyAwardListByActivityId(strategyId);
        if (Objects.isNull(strategyAwards) || strategyAwards.isEmpty()) {
            return Collections.emptyList(); // 空集合返回，避免下游处理 null 指针
        }

        // 4. PO 转换为领域 Entity（Builder 模式映射字段）
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

        // 5. 回写 Redis 缓存，实现懒加载同步，支撑后续高并发查询
        redisService.setValue(cacheKey, strategyAwardEntities);

        return strategyAwardEntities;
    }

    /**
     * 存储抽奖概率查找映射表（装配阶段核心方法）
     * 核心价值：将复杂的概率区间计算转化为 O(1) 时间复杂度的索引查找，解决高并发下的性能瓶颈。
     * 存储结构：Redis String（保存概率分母） + Redis Hash（保存随机数 -> 奖品ID 映射）
     *
     * @param key                                  策略装配唯一标识
     * @param rateRange                            概率分母（随机数生成上限，如 10000 代表万分比）
     * @param shuffleStrategyAwardSearchRateTables 打乱后的奖品ID分布映射（键：随机索引，值：奖品ID）
     */
    @Override
    public void storeStrategyAwardSearchRateTable(String key, Integer rateRange,
                                                  Map<Integer, Integer> shuffleStrategyAwardSearchRateTables) {
        // 1. 存储策略概率分母（随机数生成上限）
        String rateRangeCacheKey = Constants.RedisKey.STRATEGY_RATE_RANGE_KEY + key;
        redisService.setValue(rateRangeCacheKey, rateRange);

        // 2. 存储概率查找映射表（Redis Hash 支持高效单条查询）
        String rateTableCacheKey = Constants.RedisKey.STRATEGY_RATE_TABLE_KEY + key;
        Map<Integer, Integer> cacheRateTable = redisService.getMap(rateTableCacheKey);
        cacheRateTable.putAll(shuffleStrategyAwardSearchRateTables);
    }

    /**
     * 获取抽奖概率分母（随机数生成上限）
     *
     * @param key 策略装配唯一标识
     * @return 概率分母（随机数上限）
     * @throws AppException 策略未装配（缓存不存在）时抛出异常，防止非法抽奖请求
     */
    @Override
    public int getRateRange(String key) {
        String cacheKey = Constants.RedisKey.STRATEGY_RATE_RANGE_KEY + key;

        // 校验缓存是否存在（策略是否已装配）
        if (!redisService.isExists(cacheKey)) {
            log.error("策略概率范围缓存不存在，策略未完成装配！key: {}", key);
            throw new AppException(ResponseCode.UN_ASSEMBLED_STRATEGY_ARMORY);
        }

        // 读取并返回概率分母
        return redisService.getValue(cacheKey);
    }

    /**
     * 执行概率索引查询（根据随机数获取对应奖品ID）
     * 抽奖核心步骤：生成随机数 -> 调用该方法 -> 直接获取中奖奖品ID，O(1) 高效查询。
     *
     * @param key     策略装配唯一标识
     * @param rateKey 生成的随机索引值（范围：0 ~ 概率分母-1）
     * @return 中奖奖品ID
     */
    @Override
    public Integer getStrategyAwardAssemble(String key, int rateKey) {
        String cacheKey = Constants.RedisKey.STRATEGY_RATE_TABLE_KEY + key;
        return redisService.getFromMap(cacheKey, rateKey);
    }

    /**
     * 查询策略规则的具体配置值（带奖品ID，精准查询）
     *
     * @param strategyId 策略ID
     * @param awardId    奖品ID（可为 null，null 表示查询通用策略规则）
     * @param ruleModel  规则模型标识（如：rule_lock - 锁定规则、rule_luck_award - 幸运奖规则）
     * @return 规则配置内容（通常为 JSON 字符串或简单标识字符串）
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
     * 查询策略规则的具体配置值（通用查询，不带奖品ID）
     *
     * @param strategyId 策略ID
     * @param ruleModel  规则模型标识
     * @return 规则配置内容
     */
    @Override
    public String queryStrategyRuleValue(Long strategyId, String ruleModel) {
        return queryStrategyRuleValue(strategyId, null, ruleModel);
    }

    /**
     * 查询策略主体实体信息（带缓存）
     * <p>
     * 返回内容：策略基本描述 + 策略挂载的所有规则模型清单
     *
     * @param strategyId 策略ID
     * @return 策略领域实体（null 表示策略不存在）
     */
    @Override
    public StrategyEntity queryStrategyEntityByStrategyId(Long strategyId) {
        // 1. 构建缓存键并优先查询 Redis
        String cacheKey = Constants.RedisKey.STRATEGY_KEY + strategyId;
        StrategyEntity strategyEntity = redisService.getValue(cacheKey);
        if (Objects.nonNull(strategyEntity)) {
            return strategyEntity;
        }

        // 2. 缓存未命中，查询数据库 PO
        Strategy strategy = strategyDao.queryStrategyEntityByStrategyId(strategyId);
        if (Objects.isNull(strategy)) {
            return null;
        }

        // 3. PO 转换为 Entity 并回写缓存
        strategyEntity = StrategyEntity
                .builder()
                .strategyId(strategy.getStrategyId())
                .strategyDesc(strategy.getStrategyDesc())
                .ruleModels(strategy.getRuleModels())
                .build();
        redisService.setValue(cacheKey, strategyEntity);

        return strategyEntity;
    }

    /**
     * 查询策略规则的完整实体详情
     *
     * @param strategyId 策略ID
     * @param ruleModel  规则模型标识
     * @return 策略规则领域实体（null 表示规则不存在）
     */
    @Override
    public StrategyRuleEntity queryStrategyRule(Long strategyId, String ruleModel) {
        // 构建查询条件
        StrategyRule queryCondition = StrategyRule
                .builder()
                .strategyId(strategyId)
                .ruleModel(ruleModel)
                .build();

        // 查询数据库 PO
        StrategyRule strategyRule = strategyRuleDao.queryStrategyRule(queryCondition);
        if (Objects.isNull(strategyRule)) {
            return null;
        }

        // PO 转换为 Entity 并返回
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
     * 构建立体化规则决策树模型（支撑规则引擎递归遍历）
     * 核心逻辑：将数据库扁平结构（树主表、节点表、连线表）转换为图结构 VO，步骤如下：
     * 1.  缓存优先查询，避免重复组装
     * 2.  数据库读取三张表数据
     * 3.  构建连线分组映射（起始节点 -> 出口连线列表）
     * 4.  构建节点映射（节点信息 + 出口连线）
     * 5.  组装完整决策树并回写缓存
     *
     * @param treeId 规则树唯一标识
     * @return 完整组装的规则决策树 VO
     */
    @Override
    public RuleTreeVO queryRuleTreeVOByTreeId(String treeId) {
        // 1. 优先从缓存读取已组装好的决策树
        String cacheKey = Constants.RedisKey.RULE_TREE_VO_KEY + treeId;
        RuleTreeVO ruleTreeVO = redisService.getValue(cacheKey);
        if (Objects.nonNull(ruleTreeVO)) {
            return ruleTreeVO;
        }

        // 2. 从数据库读取决策树相关扁平数据
        RuleTree ruleTree = ruleTreeDao.queryRuleTreeByTreeId(treeId);
        List<RuleTreeNode> ruleTreeNodes = ruleTreeNodeDao.queryRuleTreeNodeListByTreeId(treeId);
        List<RuleTreeNodeLine> ruleTreeNodeLines = ruleTreeNodeLineDao.queryRuleTreeNodeLineListByTreeId(treeId);

        // 3. 判空防护（任意核心数据缺失，返回 null 避免后续空指针）
        if (Objects.isNull(ruleTree) || Objects.isNull(ruleTreeNodes) || Objects.isNull(ruleTreeNodeLines)) {
            log.warn("规则决策树数据不完整，无法组装！treeId: {}", treeId);
            return null;
        }

        // 4. 构建连线索引：按起始节点分组，快速关联节点出口连线
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
            // 不存在则创建空列表，避免手动判空
            nodeLineVOMap
                    .computeIfAbsent(line.getRuleNodeFrom(), k -> new ArrayList<>())
                    .add(lineVO);
        }

        // 5. 构建节点映射：关联节点信息与出口连线，支撑递归遍历
        Map<String, RuleTreeNodeVO> nodeVOMap = new HashMap<>();
        for (RuleTreeNode node : ruleTreeNodes) {
            String ruleKey = node
                    .getRuleKey()
                    .trim(); // 去除空格，避免比对异常
            RuleTreeNodeVO nodeVO = RuleTreeNodeVO
                    .builder()
                    .treeId(node.getTreeId())
                    .ruleKey(ruleKey)
                    .ruleDesc(node.getRuleDesc())
                    .ruleValue(node.getRuleValue())
                    .treeNodeLineVOList(nodeLineVOMap.get(ruleKey)) // 关联出口连线
                    .build();
            nodeVOMap.put(ruleKey, nodeVO);
        }

        // 6. 组装完整决策树 VO 并回写缓存
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
     * 查询奖品关联的规则模型映射（如绑定的决策树ID）
     *
     * @param strategyId 策略ID
     * @param awardId    奖品ID
     * @return 奖品规则模型 VO（null 表示无绑定规则）
     */
    @Override
    public StrategyAwardRuleModelVO queryStrategyAwardRuleModel(Long strategyId, Integer awardId) {
        StrategyAward queryCondition = StrategyAward
                .builder()
                .strategyId(strategyId)
                .awardId(awardId)
                .build();

        // 查询奖品绑定的规则模型
        String ruleModel = strategyAwardDao.queryStrategyAwardRuleModel(queryCondition);
        if (StringUtils.isBlank(ruleModel)) {
            return null;
        }

        // 封装 VO 并返回
        return StrategyAwardRuleModelVO
                .builder()
                .ruleModels(ruleModel)
                .build();
    }

    /**
     * 初始化缓存奖品库存总量（原子操作，防止并发重置）
     * 说明：仅当缓存不存在时才初始化，避免多节点部署时并发重置库存，使用 Redis AtomicLong 支撑后续原子增减。
     *
     * @param cacheKey   库存缓存键（格式：strategy_award_count_key + 策略ID + 奖品ID）
     * @param awardCount 奖品初始库存总量
     */
    @Override
    public void cacheStrategyAwardCount(String cacheKey, Integer awardCount) {
        // 原子性判断：缓存不存在才初始化，避免并发覆盖
        if (redisService.isExists(cacheKey)) {
            return;
        }
        // 初始化 Redis 原子长整型，支撑后续高频库存扣减/查询
        redisService.setAtomicLong(cacheKey, awardCount);
    }

    /**
     * 奖品库存扣减（高并发核心，防超卖）
     * 核心机制：Redis 原子预扣减 + 分布式状态锁，彻底杜绝超卖，步骤如下：
     * 1. 准入即校验：在执行 decr 之前，如果业务传入了结束时间且已过期，直接抛出异常，不再访问 Redis。
     * 2. 异常中断：一旦 decr 结果小于 0，立即抛出【库存已耗尽】异常，停止后续所有发奖逻辑。
     * 3. 锁隔离：保持 lockKey 独立，确保每一个库存序号（从 N 到 1）在分布式环境下有且仅有一个赢家。
     *
     * @param key         库存缓存键
     * @param endDateTime 活动结束时间（用于计算锁过期时间）
     * @return true：扣减成功（获得发奖资格），false：扣减失败（库存不足/锁竞争失败）
     */

    @Override
    public Boolean subtractAwardStock(String key, Date endDateTime) {
        // [1] 时间合法性前置检查：如果活动已结束，直接拒绝访问
        if (null != endDateTime && endDateTime.before(new Date())) {
            log.error("库存扣减失败，活动已结束或已过期，Key: {}", key);
            throw new AppException(ResponseCode.ACTIVITY_DATE_ERROR);
        }

        // [2] 执行原子预扣减
        long surPlus = redisService.decr(key);

        // [3] 核心改进：小于 0 说明在 Redis 层面已经超卖，直接报错中断流程
        if (surPlus < 0) {
            // 将计数器重置为 0，修复因为高并发穿透导致的负值过大问题
            redisService.setAtomicLong(key, 0L);
            log.warn("奖品库存已耗尽，禁止后续操作，Key: {}", key);
            // 抛出具体异常，让上层调用方（抽奖链路）直接失败，不再执行昂贵的后续计算
            throw new AppException(ResponseCode.STRATEGY_AWARD_STOCK_EMPTY);
        }

        // [4] 构建库存快照锁：key_剩余库存值 (例如：stock_100_5)
        // 这一步是为了防止在高并发瞬间，多个线程同时 decr 成功，但在业务层需要更细粒度的控制
        String lockKey = key + Constants.UNDERLINE + surPlus;

        // [5] 计算锁的自动释放时间，防止死锁
        long expireSeconds = 3600; // 缺省 1 小时
        if (endDateTime != null) {
            long diffMs = endDateTime.getTime() - System.currentTimeMillis();
            // 锁的有效期至少保留 10 秒，以支撑异步 Job 完成 DB 同步
            expireSeconds = Math.max(10, TimeUnit.MILLISECONDS.toSeconds(diffMs));
        }

        // [6] 占用状态锁，确保“一物一锁”
        Boolean lock = redisService.setNx(lockKey, expireSeconds, TimeUnit.SECONDS);
        if (!lock) {
            log.warn("库存快照锁占用失败，产生冲突，lockKey: {}", lockKey);
            // 锁竞争失败也视为扣减失败，抛出异常或返回 false 均可，此处返回 false 交由上层重试或放弃
        }

        return lock;
    }

    /**
     * 奖品库存扣减（无活动结束时间，默认锁配置）
     *
     * @param key 库存缓存键
     * @return true：扣减成功，false：扣减失败
     */
    @Override
    public Boolean subtractAwardStock(String key) {
        return subtractAwardStock(key, null);
    }

    /**
     * 奖品库存消耗流水入延迟队列（削峰填谷，支撑最终一致性）
     * 说明：延迟 3 秒入队，将 Redis 实时扣减平摊到 DB 异步同步，避免 DB 被高并发压垮。
     *
     * @param strategyAwardStockKeyVO 库存流水对象（包含策略ID、奖品ID）
     */
    @Override
    public void awardStockConsumeSendQueue(StrategyAwardStockKeyVO strategyAwardStockKeyVO) {
        String queueCacheKey = Constants.RedisKey.STRATEGY_AWARD_COUNT_QUERY_KEY;

        // 1. 获取 Redisson 阻塞队列（衔接下游异步消费 Job）
        RBlockingQueue<StrategyAwardStockKeyVO> blockingQueue = redisService.getBlockingQueue(queueCacheKey);
        // 2. 包装为延迟队列，实现 3 秒延迟入队，削峰填谷
        RDelayedQueue<StrategyAwardStockKeyVO> delayedQueue = redisService.getDelayedQueue(blockingQueue);

        // 3. 延迟入队，支撑后续 DB 异步同步库存
        delayedQueue.offer(strategyAwardStockKeyVO, 3, TimeUnit.SECONDS);
    }

    /**
     * 从阻塞队列获取待处理的库存扣减流水（异步消费 Job 调用）
     * 说明：下游分布式 Job 调用该方法，获取流水并同步至 DB，实现 Redis 与 DB 库存最终一致性。
     *
     * @return 库存扣减流水对象（null 表示队列为空）
     */
    @Override
    public StrategyAwardStockKeyVO takeQueueValue() {
        String queueCacheKey = Constants.RedisKey.STRATEGY_AWARD_COUNT_QUERY_KEY;
        RBlockingQueue<StrategyAwardStockKeyVO> blockingQueue = redisService.getBlockingQueue(queueCacheKey);
        return blockingQueue.poll();
    }

    /**
     * 同步更新数据库奖品库存（异步消费最终步骤）
     * 说明：将 Redis 预扣减的库存同步至 DB，实现库存最终一致性，仅处理扣减操作（库存余量-1）。
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
        strategyAwardDao.updateStrategyAwardStock(updateCondition);
    }

    /**
     * 查询单个奖品配置实体（带缓存懒加载）
     *
     * @param strategyId 策略ID
     * @param awardId    奖品ID
     * @return 奖品领域实体（null 表示奖品不存在）
     */
    @Override
    public StrategyAwardEntity queryStrategyAwardEntity(Long strategyId, Integer awardId) {
        // 1. 构建缓存键并优先查询 Redis
        String cacheKey = Constants.RedisKey.STRATEGY_AWARD_KEY + strategyId + Constants.UNDERLINE + awardId;
        StrategyAwardEntity strategyAwardEntity = redisService.getValue(cacheKey);
        if (Objects.nonNull(strategyAwardEntity)) {
            return strategyAwardEntity;
        }

        // 2. 缓存未命中，查询数据库 PO
        StrategyAward queryCondition = StrategyAward
                .builder()
                .strategyId(strategyId)
                .awardId(awardId)
                .build();
        StrategyAward strategyAward = strategyAwardDao.queryStrategyAward(queryCondition);
        if (Objects.isNull(strategyAward)) {
            return null;
        }

        // 3. PO 转换为 Entity 并回写缓存
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
     * 根据活动ID查询关联的策略ID
     *
     * @param activityId 活动ID
     * @return 策略ID（null 表示活动无关联策略）
     */
    @Override
    public Long queryStrategyIdByActivityId(Long activityId) {
        return raffleActivityDao.queryStrategyIdByActivityId(activityId);
    }

    /**
     * 查询用户今日已参与抽奖的次数
     * 计算逻辑：今日总配额 - 今日剩余配额 = 今日已消耗次数
     *
     * @param userId     用户ID
     * @param strategyId 策略ID
     * @return 今日已抽奖次数
     */
    @Override
    public Integer queryTodayUserRaffleCount(String userId, Long strategyId) {
        // 1. 策略ID 转换为 活动ID（用户抽奖次数按活动维度统计）
        Long activityId = raffleActivityDao.queryActivityIdByStrategyId(strategyId);
        if (Objects.isNull(activityId)) {
            log.warn("策略无关联活动，无法查询用户抽奖次数！strategyId: {}", strategyId);
            return 0;
        }

        // 2. 构建用户今日账户查询条件（自动获取当前日期）
        RaffleActivityAccountDay queryCondition = RaffleActivityAccountDay
                .builder()
                .userId(userId)
                .activityId(activityId)
                .build();
        queryCondition.setDay(RaffleActivityAccountDay.currentDay());

        // 3. 查询用户今日活动账户记录
        RaffleActivityAccountDay userAccountDay = raffleActivityAccountDayDao.queryActivityAccountDay(queryCondition);
        if (Objects.isNull(userAccountDay)) {
            return 0;
        }

        // 4. 计算并返回今日已抽奖次数（总配额 - 剩余配额）
        return userAccountDay.getDayCount() - userAccountDay.getDayCountSurplus();
    }

    @Override
    public Integer queryTotalUserRaffleCount(String userId, Long strategyId) {
        // 1. 根据策略 ID 查询关联的活动 ID（账户次数是按活动维度进行隔离和统计的）
        Long activityId = raffleActivityDao.queryActivityIdByStrategyId(strategyId);
        if (Objects.isNull(activityId)) {
            log.warn("查询用户总抽奖次数失败：策略未关联任何活动 | strategyId: {}", strategyId);
            return 0;
        }

        // 2. 构建活动账户查询对象
        RaffleActivityAccount queryCondition = RaffleActivityAccount
                .builder()
                .userId(userId)
                .activityId(activityId)
                .build();

        // 3. 查询用户在该活动下的总账户记录（仅包含总额度相关字段）
        RaffleActivityAccount userAccount = raffleActivityAccountDao.queryTotalUserRaffleCount(queryCondition);
        if (Objects.isNull(userAccount)) {
            log.info("用户活动账户不存在，返回已抽奖次数为 0 | userId: {} activityId: {}", userId, activityId);
            return 0;
        }

        // 4. 计算并返回该活动累计已抽奖次数：总配额 - 剩余配额 = 已消耗配额
        // 注意：这里返回的是自参加活动以来的全量消耗次数，非今日消耗
        return userAccount.getTotalCount() - userAccount.getTotalCountSurplus();
    }


    /**
     * 查询并组装权重规则配置
     * 描述：优先从缓存获取，失效时从数据库加载规则配置，并批量组装关联的奖品明细。
     * 逻辑：1.查询缓存 -> 2.解析规则 -> 3.批量预查奖品 -> 4.内存匹配组装 -> 5.回写缓存
     *
     * @param strategyId 策略 ID
     * @return 组装完成的权重规则配置列表
     */
    public List<RuleWeightVO> queryAwardRuleWeight(Long strategyId) {
        // 1. 优先从 Redis 缓存获取，命中则直接返回，降低数据库 IO
        String cacheKey = Constants.RedisKey.STRATEGY_RULE_WEIGHT_KEY + strategyId;
        List<RuleWeightVO> ruleWeightVOS = redisService.getValue(cacheKey);
        if (null != ruleWeightVOS) return ruleWeightVOS;

        // 2. 缓存未命中，查询策略规则原始配置 (如: 4000:101,102 5000:103)
        String ruleModel = DefaultChainFactory.LogicModel.RULE_WEIGHT.getCode();
        StrategyRule strategyRule = StrategyRule
                .builder()
                .strategyId(strategyId)
                .ruleModel(ruleModel)
                .build();
        String ruleValue = strategyRuleDao.queryStrategyRuleValue(strategyRule);

        // 3. 防腐校验：若规则配置为空，则直接回写空结果并返回，防止缓存穿透
        if (StringUtils.isBlank(ruleValue)) return new ArrayList<>();

        // 4. 借助领域实体解析规则字符串，转换为结构化 Map (Key: 4000, Value: [101, 102])
        StrategyRuleEntity strategyRuleEntity = StrategyRuleEntity
                .builder()
                .ruleModel(ruleModel)
                .ruleValue(ruleValue)
                .build();
        Map<String, List<Integer>> ruleWeightValues = strategyRuleEntity.getRuleValueGroup();

        // 5. 性能优化：提取所有权重档位关联的奖品 ID，合并为单一集合以便执行批量查询
        List<Integer> allAwardIds = ruleWeightValues
                // 5.1 获取 Map 中所有的 Value 集合（此时得到的是 Collection<List<Integer>>，即“列表的列表”）
                .values()
                // 5.2 使用 Stream 流进行数据处理
                .stream()
                // 5.3 扁平化处理：将多个 List<Integer> 展开并合并成一个统一的 Stream<Integer> 序列
                .flatMap(Collection::stream)
                // 5.4 去重处理：过滤掉不同权重档位中可能重复出现的奖品 ID，减少后续数据库查询压力
                .distinct()
                .collect(Collectors.toList());

        // 6. 批量获取奖品明细，并转换为 Map 结构供内存快速检索
        List<StrategyAward> strategyAwards = strategyAwardDao.queryStrategyAwardListByAwardIds(strategyId, allAwardIds);
        Map<Integer, StrategyAward> awardMap = strategyAwards
                .stream()
                .collect(Collectors.toMap(StrategyAward::getAwardId, award -> award));

        // 7. 遍历权重规则组，装配 RuleWeightVO
        ruleWeightVOS = new ArrayList<>();
        for (Map.Entry<String, List<Integer>> entry : ruleWeightValues.entrySet()) {
            String weightKey = entry.getKey();
            List<Integer> awardIds = entry.getValue();
            List<RuleWeightVO.Award> awardList = new ArrayList<>();

            // 8. 内存组装奖品信息：不再查询数据库，直接从 awardMap 获取
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

            // 9. 封装权重档位对象
            ruleWeightVOS.add(RuleWeightVO
                    .builder()
                    .ruleValue(ruleValue)
                    .weight(Integer.valueOf(weightKey))
                    .awardIds(awardIds)
                    .awardList(awardList)
                    .build());
        }

        // 10. 最终结果异步/同步回写 Redis，并返回
        redisService.setValue(cacheKey, ruleWeightVOS);

        return ruleWeightVOS;
    }

    /**
     * 查询奖品规则锁定的次数要求（批量查询）
     * 说明：批量查询 rule_lock 类型规则的配置值，转换为 树ID -> 解锁次数 的映射，支撑快速查询。
     *
     * @param treeIds 规则树ID数组（来自奖品的 ruleModels 配置）
     * @return 树ID -> 解锁次数 映射（空 Map 表示无配置）
     */
    @Override
    public Map<String, Integer> queryAwardRuleLockCount(String[] treeIds) {
        // 1. 准入校验：空数组直接返回空 Map，避免无效 SQL 查询
        if (Objects.isNull(treeIds) || treeIds.length == 0) {
            return Collections.emptyMap();
        }

        // 2. 批量查询规则锁定配置 PO
        List<RuleTreeNode> ruleTreeNodes = ruleTreeNodeDao.queryRuleLocks(treeIds);
        if (Objects.isNull(ruleTreeNodes) || ruleTreeNodes.isEmpty()) {
            log.warn("未查询到规则锁定配置！treeIds: {}", Arrays.toString(treeIds));
            return Collections.emptyMap();
        }

        // 3. 转换为 树ID -> 解锁次数 映射，带容错处理
        return ruleTreeNodes
                .stream()
                .collect(Collectors.toMap(RuleTreeNode::getTreeId, node -> {
                            try {
                                // 容错：去除空格 + 数字转换，异常返回 0
                                String ruleValue = node.getRuleValue();
                                return StringUtils.isNotBlank(ruleValue) ? Integer.parseInt(ruleValue.trim()) : 0;
                            } catch (NumberFormatException e) {
                                log.error("规则锁定次数解析失败！treeId: {}, ruleValue: {}", node.getTreeId(),
                                        node.getRuleValue(), e);
                                return 0;
                            }
                        },
                        // 冲突处理：同一树ID多条记录，保留最后一条（理论上不应发生）
                        (existingValue, newValue) -> newValue));
    }

}