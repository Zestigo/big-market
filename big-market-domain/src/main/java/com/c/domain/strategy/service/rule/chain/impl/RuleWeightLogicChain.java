package com.c.domain.strategy.service.rule.chain.impl;

import com.c.domain.strategy.repository.IStrategyRepository;
import com.c.domain.strategy.service.armory.IStrategyDispatch;
import com.c.domain.strategy.service.rule.chain.AbstractLogicChain;
import com.c.domain.strategy.service.rule.chain.factory.DefaultChainFactory;
import com.c.types.common.Constants;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author cyh
 * @description 抽奖责任链 - 权重消耗过滤节点
 * @details 业务逻辑：根据用户当前累积的积分/权重值（userScore），匹配对应的奖品池档位。
 * 匹配原则：寻找配置中【小于等于】用户当前积分的最大权重值。
 * 场景示例：配置为 "4000:101 5000:102"，若用户积分为 4500：
 * 1. 满足 <= 4500 的集合为 {4000}。
 * 2. 取最大值 4000，最终命中 101 奖品池。
 * @date 2026/01/18
 */
@Slf4j
@Component("rule_weight")
public class RuleWeightLogicChain extends AbstractLogicChain {

    @Resource
    private IStrategyRepository strategyRepository;

    @Resource
    private IStrategyDispatch strategyDispatch;

    /**
     * 权重过滤核心逻辑
     *
     * @param userId     用户ID
     * @param strategyId 策略ID
     * @return 命中权重奖品池则返回奖品ID，否则返回 null 由后续链条处理
     */
    @Override
    public DefaultChainFactory.StrategyAwardVO logic(String userId, Long strategyId) {
        String ruleModel = ruleModel();
        log.info("抽奖责任链-权重过滤开始 userId: {}, strategyId: {}, ruleModel: {}", userId, strategyId, ruleModel);

        // 1. 查询权重规则配置。格式示例："4000:101,102 5000:101,102,103"
        // 含义：累计抽奖满 4000 次，在 101,102 中抽奖；满 5000 次，在 101,102,103 中抽奖
        String ruleValue = strategyRepository.queryStrategyRuleValue(strategyId, ruleModel);
        if (StringUtils.isBlank(ruleValue)) {
            log.info("抽奖责任链-权重规则未配置，直接放行流转至下一节点. strategyId: {}", strategyId);
            return next().logic(userId, strategyId);
        }

        // 2. 解析规则配置：将配置字符串转换为有序映射 (Key: 触发阈值次数, Value: 对应奖品列表字符串)
        Map<Long, String> ruleValueMap = getAnalyticalValue(ruleValue);
        if (ruleValueMap.isEmpty()) {
            log.warn("抽奖责任链-权重规则解析为空，直接放行. strategyId: {}", strategyId);
            return next().logic(userId, strategyId);
        }

        // 3. 获取用户在该策略关联活动下的【累计抽奖次数】（作为权重判定的依据）
        Integer userRaffleCount = strategyRepository.queryTotalUserRaffleCount(userId, strategyId);

        // 4. 寻找匹配档位：在已配置的权重阈值中，找到用户已达到的最大档位 (Key <= userRaffleCount)
        Long matchedKey = ruleValueMap
                .keySet()
                .stream()
                .filter(key -> userRaffleCount >= key)
                .max(Long::compare)
                .orElse(null);

        // 5. 判定匹配结果：若命中权重档位，则执行该等级下的独立随机抽奖，并【截断】责任链后续流程
        if (null != matchedKey) {
            Integer awardId = strategyDispatch.getRandomAwardId(strategyId, ruleValueMap.get(matchedKey));
            log.info("抽奖责任链-权重匹配成功 userId: {}, strategyId: {}, 命中档位: {}, 产出奖品ID: {}", userId, strategyId, matchedKey,
                    awardId);
            return DefaultChainFactory.StrategyAwardVO
                    .builder()
                    .awardId(awardId)
                    .logicModel(ruleModel())
                    .build();
        }

        // 6. 边界处理：用户累计抽奖次数未达到任何权重门槛，放行流转至后续节点（如默认随机抽奖节点）
        log.info("抽奖责任链-权重放行（累计抽奖次数未达标） userId: {}, strategyId: {}, 累计次数: {}", userId, strategyId, userRaffleCount);
        return next().logic(userId, strategyId);
    }

    /**
     * 配置解析工具方法
     *
     * @param ruleValue 原始配置字符串，例如 "4000:101,102 5000:103"
     * @return 解析后的 Map，结构示例：{4000 -> "4000:101,102", 5000 -> "5000:103"}
     */
    private Map<Long, String> getAnalyticalValue(String ruleValue) {
        if (StringUtils.isBlank(ruleValue)) return Collections.emptyMap();

        String[] ruleValueGroups = ruleValue.split(Constants.SPACE);
        Map<Long, String> ruleValueMap = new HashMap<>();

        for (String group : ruleValueGroups) {
            if (StringUtils.isBlank(group)) continue;

            String[] parts = group.split(Constants.COLON);
            if (parts.length != 2) {
                log.error("权重规则格式异常，已忽略该组配置: {}", group);
                continue;
            }
            ruleValueMap.put(Long.parseLong(parts[0]), group);
        }
        return ruleValueMap;
    }

    @Override
    protected String ruleModel() {
        return DefaultChainFactory.LogicModel.RULE_WEIGHT.getCode();
    }
}