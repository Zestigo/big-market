package com.c.domain.strategy.service.rule.chain.impl;

import com.c.domain.strategy.repository.IStrategyRepository;
import com.c.domain.strategy.service.armory.IStrategyDispatch;
import com.c.domain.strategy.service.rule.chain.AbstractLogicChain;
import com.c.domain.strategy.service.rule.chain.factory.DefaultChainFactory;
import com.c.domain.strategy.service.rule.filter.factory.DefaultLogicFactory;
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
     * 模拟用户当前权重分值
     * TODO: 生产环境应从 Redis 缓存、数据库或上下文 Session 中获取真实数值
     */
    public long userScore = 0L;

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

        // 1. 获取策略配置值。格式示例："4000:101,102 5000:101,102,103"
        String ruleValue = strategyRepository.queryStrategyRuleValue(strategyId, ruleModel);
        if (StringUtils.isBlank(ruleValue)) {
            log.warn("抽奖责任链-权重规则未配置，直接放行流转至下一节点. strategyId: {}", strategyId);
            return next().logic(userId, strategyId);
        }

        // 2. 解析规则配置：将配置字符串转换为有序映射 (Key: 权重阈值, Value: 规则全字符串)
        Map<Long, String> ruleValueMap = getAnalyticalValue(ruleValue);
        if (ruleValueMap.isEmpty()) {
            log.warn("抽奖责任链-权重规则解析结果为空，直接放行. strategyId: {}", strategyId);
            return next().logic(userId, strategyId);
        }

        // 3. 匹配算法：在已配置的权重 Key 中，筛选出 <= userScore 的集合，并取其中的最大值
        Long matchedKey = ruleValueMap.keySet().stream().filter(key -> userScore >= key).max(Long::compare).orElse(null);

        // 4. 判定匹配结果：若命中档位，则执行该权重等级下的独立抽奖，并【接管】责任链流程
        if (null != matchedKey) {
            Integer awardId = strategyDispatch.getRandomAwardId(strategyId, ruleValueMap.get(matchedKey));
            log.info("抽奖责任链-权重匹配成功 userId: {}, strategyId: {}, 命中档位: {}, 产出奖品ID: {}", userId, strategyId, matchedKey, awardId);
            return DefaultChainFactory.StrategyAwardVO.builder().awardId(awardId).logicModel(ruleModel()).build();
        }

        // 5. 边界处理：用户积分未达到任何最低权重门槛，放行流转至后续通用抽奖逻辑
        log.info("抽奖责任链-权重放行（用户积分未达标） userId: {}, strategyId: {}", userId, strategyId);
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