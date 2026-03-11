package com.c.domain.strategy.service.armory;

import com.c.domain.strategy.model.entity.StrategyAwardEntity;
import com.c.domain.strategy.model.entity.StrategyEntity;
import com.c.domain.strategy.model.entity.StrategyRuleEntity;
import com.c.domain.strategy.repository.IStrategyRepository;
import com.c.types.common.Constants;
import com.c.types.enums.ResponseCode;
import com.c.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 抽奖策略算法抽象基类
 * 封装通用装配/调度逻辑，具体算法（O1/OLogN）由子类实现
 *
 * @author cyh
 * @date 2026/03/11
 */
@Slf4j
public abstract class AbstractStrategyAlgorithm implements IStrategyArmory, IStrategyDispatch {

    @Resource
    protected IStrategyRepository repository;

    @Override
    public boolean assembleLotteryStrategy(Long strategyId) {
        log.info("开始装配抽奖策略：strategyId = {}", strategyId);

        // 1. 查询策略关联奖品配置，无配置则装配失败
        List<StrategyAwardEntity> strategyAwardEntities = repository.queryStrategyAwardList(strategyId);
        if (null == strategyAwardEntities || strategyAwardEntities.isEmpty()) return false;

        // 2. 缓存预热：初始化奖品库存（全量/权重池共用库存）
        for (StrategyAwardEntity strategyAward : strategyAwardEntities) {
            cacheStrategyAwardCount(strategyId, strategyAward.getAwardId(), strategyAward.getAwardCount());
        }

        // 3. 全量装配：默认抽奖池（Key = 策略ID）
        calculateAndArmory(String.valueOf(strategyId), strategyAwardEntities);

        // 4. 权重规则装配：处理专属抽奖池
        StrategyEntity strategyEntity = repository.queryStrategyEntityByStrategyId(strategyId);
        if (null == strategyEntity || StringUtils.isBlank(strategyEntity.getRuleWeight())) return true;

        String ruleWeight = strategyEntity.getRuleWeight();
        StrategyRuleEntity strategyRule = repository.queryStrategyRule(strategyId, ruleWeight);
        if (null == strategyRule) {
            throw new AppException(ResponseCode.STRATEGY_RULE_WEIGHT_IS_NULL);
        }

        // 5. 按权重分组装配：每档权重独立计算量程（Key = 策略ID_权重值）
        Map<String, List<Integer>> ruleValueGroup = strategyRule.getRuleValueGroup();
        for (String key : ruleValueGroup.keySet()) {
            List<Integer> awardIds = ruleValueGroup.get(key);
            // 过滤当前权重对应的奖品子集
            List<StrategyAwardEntity> groupAwards = strategyAwardEntities
                    .stream()
                    .filter(entity -> awardIds.contains(entity.getAwardId()))
                    .collect(Collectors.toList());

            // 权重池独立计算量程并装配
            calculateAndArmory(String
                    .valueOf(strategyId)
                    .concat(Constants.UNDERLINE)
                    .concat(key), groupAwards);
        }

        return true;
    }

    /**
     * 封装概率计算+算法装配核心逻辑
     *
     * @param key    策略装配唯一标识
     * @param awards 奖品配置集合
     */
    private void calculateAndArmory(String key, List<StrategyAwardEntity> awards) {
        // 1. 计算奖品集合总概率（权重池总概率可不等于1.0）
        BigDecimal totalRate = awards
                .stream()
                .map(StrategyAwardEntity::getAwardRate)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 2. 获取最小有效概率（过滤0值，避免除零异常）
        BigDecimal minRate = awards
                .stream()
                .map(StrategyAwardEntity::getAwardRate)
                .filter(rate -> rate.compareTo(BigDecimal.ZERO) > 0)
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        // 3. 配置合法性校验：总概率/最小概率不可为0
        if (totalRate.compareTo(BigDecimal.ZERO) <= 0 || minRate.compareTo(BigDecimal.ZERO) <= 0) {
            log.error("策略装配失败 Key:{}，概率配置非法（总概率/最小概率为0）", key);
            throw new AppException(ResponseCode.STRATEGY_CONFIG_ERROR);
        }

        // 4. 动态计算量程：总概率/最小概率（向上取整，保证最小概率奖品至少1个格子）
        BigDecimal rateRange = totalRate.divide(minRate, 0, RoundingMode.CEILING);

        // 5. 调用子类具体装配算法（O1/OLogN）
        armoryAlgorithm(key, awards, rateRange, totalRate);
    }

    /**
     * 抽象装配算法：子类实现O(1)/O(LogN)具体存储逻辑
     *
     * @param key                   策略装配唯一标识
     * @param strategyAwardEntities 奖品配置列表
     * @param rateRange             概率量程
     * @param totalAwardRate        奖品总概率
     */
    protected abstract void armoryAlgorithm(String key, List<StrategyAwardEntity> strategyAwardEntities,
                                            BigDecimal rateRange, BigDecimal totalAwardRate);

    /**
     * 抽象调度算法：子类实现O(1)/O(LogN)具体抽奖寻址逻辑
     *
     * @param key 策略装配唯一标识
     * @return 命中的奖品ID
     */
    protected abstract Integer dispatchAlgorithm(String key);

    /**
     * 调度默认抽奖池：获取随机奖品ID
     *
     * @param strategyId 策略ID
     * @return 奖品ID
     */
    @Override
    public Integer getRandomAwardId(Long strategyId) {
        return dispatchAlgorithm(String.valueOf(strategyId));
    }

    /**
     * 调度指定权重抽奖池：获取随机奖品ID
     *
     * @param strategyId      策略ID
     * @param ruleWeightValue 权重值
     * @return 奖品ID
     */
    @Override
    public Integer getRandomAwardId(Long strategyId, String ruleWeightValue) {
        String key = String
                .valueOf(strategyId)
                .concat(Constants.UNDERLINE)
                .concat(ruleWeightValue);
        return dispatchAlgorithm(key);
    }

    /**
     * 扣减奖品库存（分布式原子操作）
     *
     * @param strategyId  策略ID
     * @param awardId     奖品ID
     * @param endDateTime 活动结束时间
     * @return true-扣减成功 false-失败
     */
    @Override
    public Boolean subtractAwardStock(Long strategyId, Integer awardId, Date endDateTime) {
        String cacheKey = Constants.RedisKey.STRATEGY_AWARD_COUNT_KEY + strategyId + Constants.UNDERLINE + awardId;
        return repository.subtractAwardStock(cacheKey, endDateTime);
    }

    /**
     * 初始化奖品库存至Redis缓存
     *
     * @param strategyId 策略ID
     * @param awardId    奖品ID
     * @param awardCount 奖品库存数
     */
    private void cacheStrategyAwardCount(Long strategyId, Integer awardId, Integer awardCount) {
        String cacheKey = Constants.RedisKey.STRATEGY_AWARD_COUNT_KEY + strategyId + Constants.UNDERLINE + awardId;
        repository.cacheStrategyAwardCount(cacheKey, awardCount);
    }

    /**
     * 按活动ID装配策略
     *
     * @param activityId 活动ID
     * @return true-装配成功 false-失败
     */
    @Override
    public boolean assembleLotteryStrategyByActivityId(Long activityId) {
        Long strategyId = repository.queryStrategyIdByActivityId(activityId);
        if (null == strategyId) {
            throw new AppException(ResponseCode.ACTIVITY_NOT_EXIST);
        }
        return assembleLotteryStrategy(strategyId);
    }
}