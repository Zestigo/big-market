package com.c.domain.strategy.service.armory.algorithm;

import com.c.domain.strategy.repository.IStrategyRepository;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.annotation.Resource;
import java.security.SecureRandom;

/**
 * 抽奖算法抽象基类
 * 1. 通用资源集成：统一管理策略仓储服务（Repository）及随机数生成器。
 * 2. 算法模型定义：通过内部枚举定义支持的算法类型（如 O(1) 查找表、O(logN) 二分搜索）。
 * 3. 共享逻辑封装：为不同复杂度的算法实现提供统一的随机数碰撞基础能力。
 *
 * @author cyh
 * @date 2026/03/11
 */
public abstract class AbstractAlgorithm implements IAlgorithm {

    /**
     * 策略仓储服务，负责概率查找表及奖品库存的持久化交互
     */
    @Resource
    protected IStrategyRepository repository;

    /**
     * 高安全级别的随机数生成器
     * 相比 Random，SecureRandom 具有更强的不可预测性，适合金融/抽奖等对安全性敏感的场景
     */
    protected final SecureRandom secureRandom = new SecureRandom();

    /**
     * 抽奖算法类型枚举
     * - O1: 空间换时间算法。通过预热完整的概率分布查找表，实现 O(1) 时间复杂度的快速定位。
     * - OLogN: 节省空间的算法。通过二分查找处理非预热或大跨度概率区间，时间复杂度为 O(logN)。
     */
    @Getter
    @AllArgsConstructor
    @NoArgsConstructor
    public enum Algorithm {
        /** O(1) 查找表算法 */
        O1("o1Algorithm"),

        /** O(logN) 二分搜索算法 */
        OLogN("oLogNAlgorithm");

        private String key;
    }

}