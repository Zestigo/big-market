package com.c.domain.strategy.service.armory;

import com.c.domain.strategy.model.entity.StrategyAwardEntity;
import com.c.domain.strategy.service.armory.algorithm.AbstractAlgorithm;
import com.c.domain.strategy.service.armory.algorithm.IAlgorithm;
import com.c.types.enums.ResponseCode;
import com.c.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 策略装配与调度服务
 * 核心能力：根据量程阈值自动分发至O(1)或O(LogN)算法执行装配/抽奖
 *
 * @author cyh
 * @date 2026/03/11
 */
@Slf4j
@Service
public class StrategyArmoryDispatch extends AbstractStrategyAlgorithm {

    // 注入所有IAlgorithm实现类（O1/OLogN算法）
    private final Map<String, IAlgorithm> algorithmMap;

    // 算法切换阈值：量程超100万自动使用O(LogN)算法
    private static final BigDecimal ALGORITHM_THRESHOLD_VALUE = new BigDecimal(1_000);

    public StrategyArmoryDispatch(Map<String, IAlgorithm> algorithmMap) {
        this.algorithmMap = algorithmMap;
    }

    @Override
    protected void armoryAlgorithm(String key, List<StrategyAwardEntity> strategyAwardEntities, BigDecimal rateRange,
                                   BigDecimal totalAwardRate) {

        // 算法决策：根据量程选择O1/OLogN算法
        String beanName = rateRange.compareTo(ALGORITHM_THRESHOLD_VALUE) > 0 ?
                AbstractAlgorithm.Algorithm.OLogN.getKey() : AbstractAlgorithm.Algorithm.O1.getKey();

        // 获取算法实例并执行装配
        IAlgorithm algorithm = algorithmMap.get(beanName);
        if (null == algorithm) throw new AppException(ResponseCode.UN_ERROR);
        algorithm.armoryAlgorithm(key, strategyAwardEntities, rateRange, totalAwardRate);

        // 记录算法路由信息至Redis，供抽奖调度使用
        repository.cacheStrategyArmoryAlgorithm(key, beanName);

        log.info("策略装配完成 Key:{} 选用算法:{} 量程:{}", key, beanName, rateRange);
    }

    @Override
    protected Integer dispatchAlgorithm(String key) {
        // 获取装配时记录的算法标识，实现精准路由
        String beanName = repository.queryStrategyArmoryAlgorithmFromCache(key);
        if (null == beanName) throw new AppException(ResponseCode.UN_ASSEMBLED_STRATEGY_ARMORY);

        // 调度对应算法执行抽奖
        return algorithmMap
                .get(beanName)
                .dispatchAlgorithm(key);
    }
}