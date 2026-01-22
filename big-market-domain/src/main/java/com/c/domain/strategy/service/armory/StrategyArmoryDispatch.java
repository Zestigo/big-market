package com.c.domain.strategy.service.armory;


import com.c.domain.strategy.model.entity.StrategyAwardEntity;
import com.c.domain.strategy.model.entity.StrategyEntity;
import com.c.domain.strategy.model.entity.StrategyRuleEntity;
import com.c.domain.strategy.repository.IStrategyRepository;
import com.c.types.common.Constants;
import com.c.types.enums.ResponseCode;
import com.c.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.util.*;

/**
 * 策略装配与调度服务实现类
 * * 职责：
 * 1. 策略装配：将数据库奖品配置计算为高并发友好的概率查找表并预热库存。
 * 2. 策略调度：提供基于 Redis 查找表的高性能 O(1) 随机抽取功能。
 */
@Slf4j
@Service
public class StrategyArmoryDispatch implements IStrategyArmory, IStrategyDispatch {

    @Resource
    private IStrategyRepository repository;

    /**
     * 线程安全的强随机数生成器。
     * 相比 Random，其生成的随机数更难被预测，适合抽奖等对公平性要求高的场景。
     */
    private final SecureRandom random = new SecureRandom();

    @Override
    public boolean assembleLotteryStrategy(Long strategyId) {
        log.info("开始装配抽奖策略：strategyId = {}", strategyId);

        // 1. 查询该策略下的所有奖品配置（概率、数量、ID等）
        List<StrategyAwardEntity> strategyAwardEntities = repository.queryStrategyAwardList(strategyId);
        if (null == strategyAwardEntities || strategyAwardEntities.isEmpty()) {
            log.warn("策略装配失败，未查询到奖品配置：strategyId = {}", strategyId);
            return false;
        }

        // 2. 循环处理每个奖品，将其库存预热到 Redis 缓存中
        for (StrategyAwardEntity strategyAward : strategyAwardEntities) {
            cacheStrategyAwardCount(strategyId, strategyAward.getAwardId(), strategyAward.getAwardCount());
        }

        // 3. 装配“默认全量抽奖池”（即没有任何规则限制时的通用概率表）
        assembleLotteryStrategy(String.valueOf(strategyId), strategyAwardEntities);

        // 4. 获取策略主实体，判断是否配置了“权重规则”（例如：消费满额、积分达标等对应的特殊奖池）
        StrategyEntity strategyEntity = repository.queryStrategyEntityByStrategyId(strategyId);
        if (null == strategyEntity || null == strategyEntity.getRuleWeight()) {
            return true; // 若无规则配置，则装配结束
        }

        // 5. 根据权重规则 Key，查询具体的规则详情（如：不同权重值对应哪些奖品 ID）
        String ruleWeight = strategyEntity.getRuleWeight();
        StrategyRuleEntity strategyRule = repository.queryStrategyRule(strategyId, ruleWeight);
        if (null == strategyRule) {
            log.error("策略规则装配异常：策略ID {} 缺少权重规则详情 {}", strategyId, ruleWeight);
            throw new AppException(ResponseCode.STRATEGY_RULE_WEIGHT_IS_NULL.getCode(),
                    ResponseCode.STRATEGY_RULE_WEIGHT_IS_NULL.getInfo());
        }

        // 6. 按照规则中的不同阶梯（如：1000积分档、2000积分档）分别装配独立的概率查找表
        Map<String, List<Integer>> ruleValueGroup = strategyRule.getRuleValueGroup();
        for (String key : ruleValueGroup.keySet()) {
            List<Integer> awardIds = ruleValueGroup.get(key);

            // 复制一份全量奖品列表，并剔除掉不在当前权重规则范围内的奖品
            List<StrategyAwardEntity> strategyAwardEntitiesClone = new ArrayList<>(strategyAwardEntities);
            strategyAwardEntitiesClone.removeIf(entity -> !awardIds.contains(entity.getAwardId()));

            // 执行特定权重下的概率表装配，Key 格式：strategyId_weightKey
            assembleLotteryStrategy(String.valueOf(strategyId).concat("_").concat(key), strategyAwardEntitiesClone);
        }

        log.info("策略装配完成：strategyId = {}", strategyId);
        return true;
    }

    /**
     * 核心装配算法：通过“空间换时间”，将概率转化为查找表
     */
    private void assembleLotteryStrategy(String key, List<StrategyAwardEntity> strategyAwardEntities) {
        if (strategyAwardEntities.isEmpty()) return;

        // 1. 确定最小概率值，作为概率空间的“最小刻度”
        BigDecimal minAwardRate = strategyAwardEntities.stream()
                                                       .map(StrategyAwardEntity::getAwardRate)
                                                       .min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);

        if (minAwardRate.equals(BigDecimal.ZERO)) {
            log.error("策略装配异常：存在概率为0的奖品项，Key = {}", key);
            return;
        }

        // 2. 计算当前奖池的总概率（通常为1.0）
        BigDecimal totalAwardRate = strategyAwardEntities.stream()
                                                         .map(StrategyAwardEntity::getAwardRate)
                                                         .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 3. 计算查找表所需的总长度（总概率 / 最小概率 = 格子总数）
        BigDecimal rateRange = totalAwardRate.divide(minAwardRate, 0, RoundingMode.CEILING);

        // 4. 初始化查找表，将奖品 ID 按比例填充到 List 中
        List<Integer> strategyAwardSearchRateTables = new ArrayList<>(rateRange.intValue());
        for (StrategyAwardEntity strategyAward : strategyAwardEntities) {
            Integer awardId = strategyAward.getAwardId();
            BigDecimal awardRate = strategyAward.getAwardRate();

            // 计算当前奖品在总刻度中应占有的格子数量
            int count = rateRange.multiply(awardRate).divide(totalAwardRate, 0, RoundingMode.CEILING).intValue();
            for (int i = 0; i < count; i++) {
                strategyAwardSearchRateTables.add(awardId);
            }
        }

        // 5. 对 List 进行乱序操作，确保奖品在表中的分布是离散均匀的
        Collections.shuffle(strategyAwardSearchRateTables);

        // 6. 将乱序后的结果转换为 LinkedHashMap（保证顺序），并持久化到 Redis
        Map<Integer, Integer> shuffleStrategyAwardSearchRateTables = new LinkedHashMap<>();
        for (int i = 0; i < strategyAwardSearchRateTables.size(); i++) {
            shuffleStrategyAwardSearchRateTables.put(i, strategyAwardSearchRateTables.get(i));
        }

        // 调用 Repository 存储查找表数据，并记录表的大小以便后续取随机数
        repository.storeStrategyAwardSearchRateTable(key, shuffleStrategyAwardSearchRateTables.size(), shuffleStrategyAwardSearchRateTables);
        log.debug("策略概率表装配完成，Key: {}, 范围: {}", key, shuffleStrategyAwardSearchRateTables.size());
    }

    @Override
    public Integer getRandomAwardId(Long strategyId) {
        // 直接使用 strategyId 作为 Key 获取默认奖池的结果
        return getRandomAwardId(String.valueOf(strategyId));
    }

    @Override
    public Integer getRandomAwardId(Long strategyId, String ruleWeightValue) {
        // 拼接带有权重规则标识的 Key，获取特定奖池的结果
        String key = String.valueOf(strategyId).concat("_").concat(ruleWeightValue);
        return getRandomAwardId(key);
    }

    /**
     * 抽取逻辑核心实现
     */
    private Integer getRandomAwardId(String key) {
        // 从缓存/库中获取该概率查找表的总长度（刻度范围）
        int rateRange = repository.getRateRange(key);
        if (rateRange == 0) {
            log.error("执行抽奖失败：概率查找表未装配或为空，Key = {}", key);
            return null;
        }
        // 生成一个 [0, rateRange) 之间的随机索引，直接定位到奖品 ID
        return repository.getStrategyAwardAssemble(key, random.nextInt(rateRange));
    }

    @Override
    public Boolean subtractAwardStock(Long strategyId, Integer awardId) {
        // 构造奖品库存对应的缓存 Key
        String cacheKey = Constants.RedisKey.STRATEGY_AWARD_COUNT_KEY + strategyId + Constants.UNDERLINE + awardId;
        // 执行原子扣减操作
        return repository.subtractAwardStock(cacheKey);
    }

    /**
     * 将奖品实时库存数据缓存到持久化层
     */
    private void cacheStrategyAwardCount(Long strategyId, Integer awardId, Integer awardCount) {
        String cacheKey = Constants.RedisKey.STRATEGY_AWARD_COUNT_KEY + strategyId + Constants.UNDERLINE + awardId;
        repository.cacheStrategyAwardCount(cacheKey, awardCount);
    }
}