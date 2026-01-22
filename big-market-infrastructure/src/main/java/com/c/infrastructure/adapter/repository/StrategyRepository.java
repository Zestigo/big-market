package com.c.infrastructure.adapter.repository;

import com.c.domain.strategy.model.entity.StrategyAwardEntity;
import com.c.domain.strategy.model.entity.StrategyEntity;
import com.c.domain.strategy.model.entity.StrategyRuleEntity;
import com.c.domain.strategy.model.vo.StrategyAwardRuleModelVO;
import com.c.domain.strategy.repository.IStrategyRepository;
import com.c.infrastructure.dao.IStrategyAwardDao;
import com.c.infrastructure.dao.IStrategyDao;
import com.c.infrastructure.dao.IStrategyRuleDao;
import com.c.infrastructure.dao.po.Strategy;
import com.c.infrastructure.dao.po.StrategyAward;
import com.c.infrastructure.dao.po.StrategyRule;
import com.c.infrastructure.redis.IRedisService;
import com.c.types.common.Constants;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @description 策略仓储实现服务
 * 1. 负责领域模型 Entity 与 数据库对象 PO 的转换
 * 2. 封装数据来源：Redis 缓存与 MySQL 数据库的协同处理（Cache Aside Pattern）
 * 3. 屏蔽底层存储介质的差异，为领域层提供统一的数据访问接口
 */
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

    /**
     * 查询策略奖品配置列表
     * 逻辑：优先查询 Redis 缓存，失效时查询数据库并回写缓存
     *
     * @param strategyId 策略ID
     * @return 策略奖品实体列表
     */
    @Override
    public List<StrategyAwardEntity> queryStrategyAwardList(Long strategyId) {
        // 1. 定义缓存 Key (例如: big_market_strategy_award_key_100001)
        String cacheKey = Constants.RedisKey.STRATEGY_AWARD_KEY + strategyId;

        // 2. 尝试从 Redis 读取缓存数据
        List<StrategyAwardEntity> strategyAwardEntities = redisService.getValue(cacheKey);
        if (null != strategyAwardEntities && !strategyAwardEntities.isEmpty()) {
            return strategyAwardEntities;
        }

        // 3. 缓存缺失，从数据库中查询持久化对象 (PO)
        List<StrategyAward> strategyAwards = strategyAwardDao.queryStrategyAwardListByStrategyId(strategyId);

        // 4. 将持久化对象 (PO) 转换为领域实体对象 (Entity)，确保领域层不直接依赖基础层 PO
        strategyAwardEntities = new ArrayList<>(strategyAwards.size());
        for (StrategyAward strategyAward : strategyAwards) {
            StrategyAwardEntity strategyAwardEntity = StrategyAwardEntity.builder().strategyId(strategyAward.getStrategyId())
                                                                         .awardId(strategyAward.getAwardId())
                                                                         .awardCount(strategyAward.getAwardCount())
                                                                         .awardCountSurplus(strategyAward.getAwardCountSurplus())
                                                                         .awardRate(strategyAward.getAwardRate()).build();
            strategyAwardEntities.add(strategyAwardEntity);
        }

        // 5. 将转换后的实体列表存入 Redis，以便下次直接获取
        redisService.setValue(cacheKey, strategyAwardEntities);

        return strategyAwardEntities;
    }

    /**
     * 存储概率查找表（装配阶段使用）
     *
     * @param key                                  缓存标识 (strategyId 或 activityId)
     * @param rateRange                            概率范围值 (如 10000)
     * @param shuffleStrategyAwardSearchRateTables 打乱后的奖品概率查找表 (Map<随机位, 奖品ID>)
     */
    @Override
    public void storeStrategyAwardSearchRateTable(String key, Integer rateRange,
                                                  Map<Integer, Integer> shuffleStrategyAwardSearchRateTables) {
        // 1. 存储概率范围值：用于后续根据随机数进行模运算或范围限定
        redisService.setValue(Constants.RedisKey.STRATEGY_RATE_RANGE_KEY + key, rateRange);

        // 2. 获取 Redis 中的 Hash 结构，将随机概率查找表全部存入
        // 使用 Hash 结构可以快速 O(1) 定位到随机数对应的奖品
        Map<Integer, Integer> cacheRateTable = redisService.getMap(Constants.RedisKey.STRATEGY_RATE_TABLE_KEY + key);
        cacheRateTable.putAll(shuffleStrategyAwardSearchRateTables);
    }

    /**
     * 获取抽奖概率范围值
     *
     * @param key 策略标识
     * @return 范围值 (如 0-10000)
     */
    @Override
    public int getRateRange(String key) {
        String cacheKey = Constants.RedisKey.STRATEGY_RATE_RANGE_KEY + key;
        // 如果缓存不存在，建议此处应有防御性判断，当前直接返回
        return redisService.getValue(cacheKey);
    }

    /**
     * 从随机概率查找表中获取本次中奖的奖品ID
     *
     * @param key     策略标识
     * @param rateKey 随机产生的索引值
     * @return 奖品ID
     */
    @Override
    public Integer getStrategyAwardAssemble(String key, int rateKey) {
        // 直接从 Redis Hash 中通过 key (随机索引) 获取对应的 value (奖品ID)
        return redisService.getFromMap(Constants.RedisKey.STRATEGY_RATE_TABLE_KEY + key, rateKey);
    }

    /**
     * 查询策略规则配置的具体值 (rule_value)
     * 例如：查询"抽奖N次必中"规则下的次数限制
     */
    @Override
    public String queryStrategyRuleValue(Long strategyId, Integer awardId, String ruleModel) {
        StrategyRule strategyRule = StrategyRule.builder().strategyId(strategyId).awardId(awardId).ruleModel(ruleModel).build();
        return strategyRuleDao.queryStrategyRuleValue(strategyRule);
    }

    @Override
    public String queryStrategyRuleValue(Long strategyId, String ruleModel) {
        return queryStrategyRuleValue(strategyId, null, ruleModel);
    }

    /**
     * 查询策略主体信息实体
     *
     * @param strategyId 策略ID
     * @return 策略实体 (包含规则模型列表等)
     */
    @Override
    public StrategyEntity queryStrategyEntityByStrategyId(Long strategyId) {
        // 1. 优先查缓存
        String cacheKey = Constants.RedisKey.STRATEGY_STRATEGY_KEY + strategyId;
        StrategyEntity strategyEntity = redisService.getValue(cacheKey);
        if (null != strategyEntity) return strategyEntity;

        // 2. 查数据库
        Strategy strategy = strategyDao.queryStrategyEntityByStrategyId(strategyId);
        if (null == strategy) return null; // 防御性判断

        // 3. PO 转 Entity
        strategyEntity = StrategyEntity.builder().strategyId(strategy.getStrategyId()).strategyDesc(strategy.getStrategyDesc())
                                       .ruleModels(strategy.getRuleModels()) // 注意：此处 PO 里的
                                       // String[] 或 String 需与 Entity 匹配
                                       .build();

        // 4. 写回缓存
        redisService.setValue(cacheKey, strategyEntity);

        return strategyEntity;
    }

    /**
     * 查询具体的策略规则实体 (带有规则类型、说明等详细信息)
     *
     * @param strategyId 策略ID
     * @param ruleModel  规则模型标识 (如: "back_list", "strategy_luck_award")
     */
    @Override
    public StrategyRuleEntity queryStrategyRule(Long strategyId, String ruleModel) {
        // 1. 构建查询参数对象
        StrategyRule strategyRuleReq = StrategyRule.builder().strategyId(strategyId).ruleModel(ruleModel).build();

        // 2. 查询数据库
        StrategyRule strategyRuleRes = strategyRuleDao.queryStrategyRule(strategyRuleReq);
        if (null == strategyRuleRes) return null;

        // 3. PO 转 Entity (包含 ruleValue: 规则配置内容, ruleType: 规则类型)
        return StrategyRuleEntity.builder().strategyId(strategyRuleRes.getStrategyId()).awardId(strategyRuleRes.getAwardId())
                                 .ruleType(strategyRuleRes.getRuleType()).ruleModel(strategyRuleRes.getRuleModel())
                                 .ruleValue(strategyRuleRes.getRuleValue()).ruleDesc(strategyRuleRes.getRuleDesc()).build();
    }

    @Override
    public StrategyAwardRuleModelVO queryStrategyAwardRuleModel(Long strategyId, Integer awardId) {
        StrategyAward strategyAward = StrategyAward.builder().strategyId(strategyId).awardId(awardId).build();
        String ruleModel = strategyAwardDao.queryStrategyAwardRuleModel();
        return StrategyAwardRuleModelVO.builder().ruleModels(ruleModel).build();
    }
}