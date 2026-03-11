package com.c.domain.strategy.service.armory.algorithm.impl;

import com.c.domain.strategy.model.entity.StrategyAwardEntity;
import com.c.domain.strategy.service.armory.algorithm.AbstractAlgorithm;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * 抽奖算法 - O(1) 索引映射实现
 * 适用场景：概率量程较小，追求极致抽奖性能（直接索引寻址）
 *
 * @author cyh
 */
@Slf4j
@Component("o1Algorithm")
public class O1Algorithm extends AbstractAlgorithm {

    /**
     * 装配O(1)概率索引表：构建随机索引-奖品ID映射（洗牌后持久化）
     *
     * @param key                   策略装配唯一标识
     * @param strategyAwardEntities 奖品配置列表
     * @param rateRange             概率量程
     * @param totalAwardRate        奖品总概率
     */
    @Override
    public void armoryAlgorithm(String key, List<StrategyAwardEntity> strategyAwardEntities, BigDecimal rateRange,
                                BigDecimal totalAwardRate) {
        int range = rateRange.intValue();
        List<Integer> awardSearchTables = new ArrayList<>(range);

        // 1. 按概率分配索引位置（保底：有概率至少分配1个位置）
        for (StrategyAwardEntity award : strategyAwardEntities) {
            // 计算当前奖品应分配的索引数量（向下取整）
            int count = rateRange
                    .multiply(award.getAwardRate())
                    .divide(totalAwardRate, 0, RoundingMode.FLOOR)
                    .intValue();

            // 保底逻辑：概率>0则至少占1个索引位
            if (count == 0 && award
                    .getAwardRate()
                    .compareTo(BigDecimal.ZERO) > 0) {
                count = 1;
            }

            // 填充当前奖品的索引位置
            for (int i = 0; i < count; i++) {
                awardSearchTables.add(award.getAwardId());
            }
        }

        // 2. 填充缝隙：补全因舍入产生的索引空缺（用概率最大的奖品填充）
        if (awardSearchTables.size() < range) {
            int gap = range - awardSearchTables.size();
            // 获取概率最大的奖品ID（通常为“谢谢参与”）
            Integer maxRateAwardId = strategyAwardEntities
                    .stream()
                    .max(Comparator.comparing(StrategyAwardEntity::getAwardRate))
                    .map(StrategyAwardEntity::getAwardId)
                    .get();

            // 填充空缺索引
            for (int i = 0; i < gap; i++) {
                awardSearchTables.add(maxRateAwardId);
            }
        }

        // 3. 洗牌打乱顺序，避免连续相同奖品，提升随机性
        Collections.shuffle(awardSearchTables);
        int finalSize = awardSearchTables.size();

        // 构建索引-奖品ID映射（LinkedHashMap保证顺序）
        Map<Integer, Integer> shuffleMap = new LinkedHashMap<>((int) (finalSize / 0.75f) + 1);
        for (int i = 0; i < finalSize; i++) {
            shuffleMap.put(i, awardSearchTables.get(i));
        }

        // 持久化索引表到缓存
        repository.storeStrategyAwardSearchRateTable(key, finalSize, shuffleMap);
    }

    /**
     * 执行O(1)抽奖寻址：随机索引直接获取奖品ID
     *
     * @param key 策略装配唯一标识
     * @return 命中的奖品ID
     */
    @Override
    public Integer dispatchAlgorithm(String key) {
        // 获取概率量程，生成随机索引
        int rateRange = repository.getRateRange(key);
        int randomIndex = secureRandom.nextInt(rateRange);

        // 直接索引寻址，O(1)时间复杂度
        return repository.getStrategyAwardAssemble(key, randomIndex);
    }
}