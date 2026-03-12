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
     * 装配抽奖策略算法：将奖品概率分布映射到递增的区间轴上
     * * @param key                    策略标识
     *
     * @param strategyAwardEntities 奖品配置列表
     * @param rateRange             量程范围（通常为10000或65535）
     * @param totalAwardRate        总概率和
     */
    public void armoryAlgorithm(String key, List<StrategyAwardEntity> strategyAwardEntities, BigDecimal rateRange,
                                BigDecimal totalAwardRate) {

        // 此处选择 TreeMap 的原因：
        // 1. 实现 NavigableMap 接口，提供 ceilingEntry/Key 等导航方法，支持 O(log N) 的区间检索。
        // 2. 局部变量：该 Map 在方法内部创建，属于线程私有（栈封闭），无需担心并发抢夺，故使用 TreeMap 性能优于 ConcurrentSkipListMap。
        NavigableMap<Integer, Integer> rangeMap = new TreeMap<>();

        BigDecimal cursor = BigDecimal.ZERO; // 累加游标，用于记录区间上限

        for (StrategyAwardEntity award : strategyAwardEntities) {
            // 计算当前奖品占据的区间长度（向下取整，确保不超出总权重）
            BigDecimal count = rateRange
                    .multiply(award.getAwardRate())
                    .divide(totalAwardRate, 0, RoundingMode.FLOOR);

            // 【保底策略】概率大于0但计算长度为0时，强行分配1个单位长度，防止小概率奖品“消失”
            if (count.compareTo(BigDecimal.ZERO) == 0 && award
                    .getAwardRate()
                    .compareTo(BigDecimal.ZERO) > 0) {
                count = BigDecimal.ONE;
            }

            // 累加游标，构建区间闭环。例如：奖品1(0-20], 奖品2(20-50]...
            cursor = cursor.add(count);

            // Key 存储区间上限，Value 存储奖品ID
            rangeMap.put(cursor.intValue(), award.getAwardId());
        }

        // 【存储逻辑】将构建好的区间表持久化。
        // 抽奖时将生成 [1, cursor] 之间的随机数，利用 rangeMap.ceilingKey(random) 瞬间定位奖品。
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