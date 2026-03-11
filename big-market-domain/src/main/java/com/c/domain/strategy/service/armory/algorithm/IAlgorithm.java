package com.c.domain.strategy.service.armory.algorithm;

import com.c.domain.strategy.model.entity.StrategyAwardEntity;

import java.math.BigDecimal;
import java.util.List;

/**
 * 抽奖算法引擎接口
 * 定义抽奖算法的两个核心阶段：
 * 1. 算法预热（Armory）：计算概率分布并构建高性能查找结构。
 * 2. 算法调度（Dispatch）：基于预热结果进行高效的随机抽奖定位。
 *
 * @author cyh
 * @date 2026/03/11
 */
public interface IAlgorithm {

    /**
     * 算法装配与预热
     * 1. 空间映射：根据奖品概率和总概率范围（rateRange），计算各奖品在查找表中的占位。
     * 2. 查找表构建：生成基于 Redis 或内存的概率索引表，将概率计算转化为索引查找。
     * 3. 数据打散：对索引表进行 Shuffle 处理，确保随机分布的均匀性。
     *
     * @param key                   缓存及检索的唯一标识（通常包含策略ID与权重标识）
     * @param strategyAwardEntities 策略关联的奖品实体列表（包含奖品ID与对应概率）
     * @param rateRange             概率范围（如 0.0001 对应 10000 范围的分母）
     */
    void armoryAlgorithm(String key, List<StrategyAwardEntity> strategyAwardEntities,
                         BigDecimal rateRange, BigDecimal totalAwardRate);

    /**
     * 算法调度执行
     * 1. 随机碰撞：生成 0 至 rateRange 之间的随机整数。
     * 2. 快速索引：直接从预热好的查找表中通过随机数定位奖品 ID。
     * 3. 性能保障：保证在 O(1) 的时间复杂度内完成中奖结果的定位。
     *
     * @param key 算法对应的唯一标识
     * @return 最终中奖的奖品 ID（若未配置或异常则返回 null/默认值）
     */
    Integer dispatchAlgorithm(String key);

}