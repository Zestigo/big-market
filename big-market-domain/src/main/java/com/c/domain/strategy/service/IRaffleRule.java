package com.c.domain.strategy.service;

import java.util.Map;

/**
 * 抽奖规则服务接口
 * 负责查询与抽奖逻辑相关的规则配置，如奖品解锁门槛、次数限制等
 *
 * @author cyh
 * @since 2026/02/04
 */
public interface IRaffleRule {

    /**
     * 批量查询奖品规则锁定次数配置
     *
     * @param treeIds 规则树 ID 集合（对应各奖品配置的规则树）
     * @return Map 映射（Key: 规则树 ID, Value: 解锁所需抽奖次数）
     */
    Map<String, Integer> queryAwardRuleLockCount(String[] treeIds);
}