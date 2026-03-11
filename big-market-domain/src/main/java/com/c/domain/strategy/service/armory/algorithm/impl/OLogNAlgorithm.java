package com.c.domain.strategy.service.armory.algorithm.impl;

import com.c.domain.strategy.model.entity.StrategyAwardEntity;
import com.c.domain.strategy.service.armory.algorithm.AbstractAlgorithm;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * 抽奖算法 - O(LogN) 区间映射实现
 * 适用场景：1.概率精度极高（避免O(1)映射表内存溢出） 2.内存资源敏感（以计算时间换存储空间）
 *
 * @author cyh
 * @date 2026/03/11
 */
@Slf4j
@Component("oLogNAlgorithm")
public class OLogNAlgorithm extends AbstractAlgorithm {

    /**
     * 装配O(LogN)概率区间表：通过TreeMap构建区间上限-奖品ID映射
     *
     * @param key                   策略装配唯一标识
     * @param strategyAwardEntities 奖品配置列表
     * @param rateRange             概率量程
     * @param totalAwardRate        奖品总概率
     */
    @Override
    public void armoryAlgorithm(String key, List<StrategyAwardEntity> strategyAwardEntities, BigDecimal rateRange,
                                BigDecimal totalAwardRate) {
        // 有序区间映射表：存储区间上限 -> 奖品ID
        NavigableMap<Integer, Integer> rangeMap = new TreeMap<>();
        BigDecimal cursor = BigDecimal.ZERO; // 区间游标，累计奖品区间上限

        for (StrategyAwardEntity award : strategyAwardEntities) {
            // 计算当前奖品的区间长度（向下取整）
            BigDecimal count = rateRange
                    .multiply(award.getAwardRate())
                    .divide(totalAwardRate, 0, RoundingMode.FLOOR);

            // 保底处理：概率>0但区间长度为0时，至少分配1个区间
            if (count.compareTo(BigDecimal.ZERO) == 0 && award
                    .getAwardRate()
                    .compareTo(BigDecimal.ZERO) > 0) {
                count = BigDecimal.ONE;
            }

            // 累加游标，构建区间上限
            cursor = cursor.add(count);
            rangeMap.put(cursor.intValue(), award.getAwardId());
        }

        // 存储区间表：以实际累计游标值作为量程，确保随机数边界安全
        repository.storeStrategyAwardSearchRateTable(key, cursor.intValue(), rangeMap);
    }

    /**
     * 执行O(LogN)抽奖寻址：通过红黑树二分查找匹配奖品
     *
     * @param key 策略装配唯一标识
     * @return 命中的奖品ID
     */
    @Override
    public Integer dispatchAlgorithm(String key) {
        // 获取概率量程和区间映射表
        int rateRange = repository.getRateRange(key);
        NavigableMap<Integer, Integer> rangeMap = repository.getRangeMap(key);

        // 生成[1, rateRange]随机数，查找第一个大于等于该值的区间上限
        int random = secureRandom.nextInt(rateRange) + 1;
        return rangeMap
                .ceilingEntry(random)
                .getValue();
    }
}