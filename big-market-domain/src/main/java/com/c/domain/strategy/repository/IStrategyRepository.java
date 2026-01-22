package com.c.domain.strategy.repository;

import com.c.domain.strategy.model.entity.StrategyAwardEntity;
import com.c.domain.strategy.model.entity.StrategyEntity;
import com.c.domain.strategy.model.entity.StrategyRuleEntity;
import com.c.domain.strategy.model.vo.StrategyAwardRuleModelVO;

import java.util.List;
import java.util.Map;

/**
 * iStrategy 仓库
 *
 * @author cyh
 * @date 2026/01/18
 */
public interface IStrategyRepository {
    // 根据策略ID查询奖项列表
    List<StrategyAwardEntity> queryStrategyAwardList(Long strategyId);

    // 存储概率查找表到Redis（key=索引，value=奖品ID）
    void storeStrategyAwardSearchRateTable(String key, Integer rateRange, Map<Integer, Integer> shuffleStrategyAwardSearchRateTables);

    // 从Redis获取概率范围（比如1000=总共有1000个抽奖索引）
    int getRateRange(String strategyId);

    // 根据随机索引从Redis获取奖品ID
    Integer getStrategyAwardAssemble(String key, int rateKey);
    String queryStrategyRuleValue(Long strategyId,  String ruleModel);

    String queryStrategyRuleValue(Long strategyId, Integer awardId, String ruleModel);

    StrategyEntity queryStrategyEntityByStrategyId(Long strategyId);

    StrategyRuleEntity queryStrategyRule(Long strategyId, String ruleModel);

    StrategyAwardRuleModelVO queryStrategyAwardRuleModel(Long strategyId, Integer awardId);
}