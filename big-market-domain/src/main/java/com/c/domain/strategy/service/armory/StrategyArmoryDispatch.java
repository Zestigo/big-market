package com.c.domain.strategy.service.armory;

import com.c.domain.strategy.model.entity.StrategyAwardEntity;
import com.c.domain.strategy.model.entity.StrategyEntity;
import com.c.domain.strategy.model.entity.StrategyRuleEntity;
import com.c.domain.strategy.repository.IStrategyRepository;
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
 * 策略装配库实现类（策略武器库）
 * 职责：负责将数据库中的抽奖策略配置装配到内存/缓存中，并提供高效的随机抽奖分发能力。
 */
@Slf4j
@Service
public class StrategyArmoryDispatch implements IStrategyArmory, IStrategyDispatch {

    @Resource
    private IStrategyRepository repository;

    /**
     * 线程安全的随机数生成器（SecureRandom 适合高并发且对安全性有要求的场景）
     */
    private final SecureRandom random = new SecureRandom();

    @Override
    public boolean assembleLotteryStrategy(Long strategyId) {
        log.info("开始装配抽奖策略：strategyId = {}", strategyId);

        // 1. 查询策略奖品配置
        List<StrategyAwardEntity> strategyAwardEntities = repository.queryStrategyAwardList(strategyId);
        if (null == strategyAwardEntities || strategyAwardEntities.isEmpty()) {
            log.warn("策略装配失败，未查询到奖品配置：strategyId = {}", strategyId);
            return false;
        }

        // 2. 装配默认的全量抽奖概率表（无权重规则限制）
        assembleLotteryStrategy(String.valueOf(strategyId), strategyAwardEntities);

        // 3. 查询策略实体，检查是否存在“权重规则”（例如：满1000积分、满2000积分对应不同的奖池）
        StrategyEntity strategyEntity = repository.queryStrategyEntityByStrategyId(strategyId);
        if (null == strategyEntity) return true; // 若无策略配置，装配完成

        String ruleWeight = strategyEntity.getRuleWeight();
        if (null == ruleWeight) return true; // 若无权重规则配置，装配完成

        // 4. 加载具体的权重规则详情（RuleValueGroup 存储了权重值与可用奖品ID的映射）
        StrategyRuleEntity strategyRule = repository.queryStrategyRule(strategyId, ruleWeight);
        if (null == strategyRule) {
            log.error("策略规则装配异常：策略ID {} 配置了权重规则 {} 但未找到规则详情", strategyId, ruleWeight);
            throw new AppException(ResponseCode.STRATEGY_RULE_WEIGHT_IS_NULL.getCode(), ResponseCode.STRATEGY_RULE_WEIGHT_IS_NULL.getInfo());
        }

        // 5. 按照权重规则分别装配不同的概率表
        Map<String, List<Integer>> ruleValueGroup = strategyRule.getRuleValueGroup();
        for (String key : ruleValueGroup.keySet()) {
            List<Integer> awardIds = ruleValueGroup.get(key);
            // 过滤出该权重下允许抽取的奖品子集
            List<StrategyAwardEntity> strategyAwardEntitiesClone = new ArrayList<>(strategyAwardEntities);
            strategyAwardEntitiesClone.removeIf(entity -> !awardIds.contains(entity.getAwardId()));

            // 装配特定权重的概率表，Key格式：strategyId_weightKey
            assembleLotteryStrategy(String.valueOf(strategyId).concat("_").concat(key), strategyAwardEntitiesClone);
        }

        log.info("策略装配完成：strategyId = {}", strategyId);
        return true;
    }

    /**
     * 核心装配逻辑：将奖品列表转换为基于查找表的离散概率分布
     * 算法原理：通过最小概率确定刻度，将[0,1]区间放大为整数区间，利用数组/Map存储，实现O(1)复杂度的随机抽取。
     */
    private void assembleLotteryStrategy(String key, List<StrategyAwardEntity> strategyAwardEntities) {
        if (strategyAwardEntities.isEmpty()) return;

        // 1. 获取最小概率值，用于确定查找表的大小（刻度系数）
        BigDecimal minAwardRate = strategyAwardEntities.stream()
                                                       .map(StrategyAwardEntity::getAwardRate)
                                                       .min(BigDecimal::compareTo)
                                                       .orElse(BigDecimal.ZERO);

        if (minAwardRate.equals(BigDecimal.ZERO)) {
            log.error("策略装配异常：奖品列表中存在概率为0的项，Key = {}", key);
            return;
        }

        // 2. 计算总概率（理想情况下应为1.0）
        BigDecimal totalAwardRate = strategyAwardEntities.stream()
                                                         .map(StrategyAwardEntity::getAwardRate)
                                                         .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 3. 计算概率刻度范围：rateRange = 总概率 / 最小概率（向上取整）
        // 例如：总1.0，最小0.01，则刻度为100；若最小为0.0001，刻度为10000
        BigDecimal rateRange = totalAwardRate.divide(minAwardRate, 0, RoundingMode.CEILING);

        // 4. 生成查找表
        List<Integer> strategyAwardSearchRateTables = new ArrayList<>(rateRange.intValue());
        for (StrategyAwardEntity strategyAward : strategyAwardEntities) {
            Integer awardId = strategyAward.getAwardId();
            BigDecimal awardRate = strategyAward.getAwardRate();

            // 计算当前奖品在刻度范围内应占用的格子数
            // 使用 multiply(rateRange / totalAwardRate) 确保比例准确
            int count = rateRange.multiply(awardRate).divide(totalAwardRate, 0, RoundingMode.CEILING).intValue();
            for (int i = 0; i < count; i++) {
                strategyAwardSearchRateTables.add(awardId);
            }
        }

        // 5. 乱序处理：增加随机分布性
        Collections.shuffle(strategyAwardSearchRateTables);

        // 6. 存储到存储层（如Redis）
        // 使用Map存储：Key=序号(0-99), Value=奖品ID
        Map<Integer, Integer> shuffleStrategyAwardSearchRateTables = new LinkedHashMap<>();
        for (int i = 0; i < strategyAwardSearchRateTables.size(); i++) {
            shuffleStrategyAwardSearchRateTables.put(i, strategyAwardSearchRateTables.get(i));
        }

        repository.storeStrategyAwardSearchRateTable(key, shuffleStrategyAwardSearchRateTables.size(), shuffleStrategyAwardSearchRateTables);
        log.debug("策略概率表装配成功，Key: {}, 查找表大小: {}", key, shuffleStrategyAwardSearchRateTables.size());
    }

    @Override
    public Integer getRandomAwardId(Long strategyId) {
        return getRandomAwardId(String.valueOf(strategyId));
    }

    @Override
    public Integer getRandomAwardId(Long strategyId, String ruleWeightValue) {
        String key = String.valueOf(strategyId).concat("_").concat(ruleWeightValue);
        return getRandomAwardId(key);
    }

    /**
     * 公共抽取逻辑
     */
    private Integer getRandomAwardId(String key) {
        // 1. 从库中获取该策略的概率范围值
        int rateRange = repository.getRateRange(key);
        if (rateRange == 0) {
            log.error("获取随机奖品失败：未找到策略装配信息，Key = {}", key);
            return null;
        }
        // 2. 生成 [0, rateRange) 之间的随机索引，直接通过O(1)查询获取结果
        return repository.getStrategyAwardAssemble(key, random.nextInt(rateRange));
    }
}