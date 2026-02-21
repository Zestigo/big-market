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
 * 抽奖责任链 - 权重消耗过滤节点
 * 业务逻辑：根据用户当前累积的抽奖次数，匹配对应的奖品池档位。
 *
 * @author cyh
 * @date 2026/02/21
 */
@Slf4j
@Component("rule_weight")
public class RuleWeightLogicChain extends AbstractLogicChain {

    /* 策略仓储服务 */
    @Resource
    private IStrategyRepository strategyRepository;

    /* 策略调度服务 */
    @Resource
    private IStrategyDispatch strategyDispatch;

    @Override
    public DefaultChainFactory.StrategyAwardVO logic(String userId, Long strategyId) {
        String ruleModel = ruleModel();
        log.info("抽奖责任链-权重过滤开始 userId: {}, strategyId: {}, ruleModel: {}", userId, strategyId, ruleModel);

        // 1. 查询权重规则配置。格式示例："4000:101,102 5000:101,102,103"
        String ruleValue = strategyRepository.queryStrategyRuleValue(strategyId, ruleModel);
        if (StringUtils.isBlank(ruleValue)) {
            log.info("抽奖责任链-权重规则未配置，直接放行. strategyId: {}", strategyId);
            return nextLogic(userId, strategyId);
        }

        // 2. 解析规则配置
        Map<Long, String> ruleValueMap = getAnalyticalValue(ruleValue);
        if (ruleValueMap.isEmpty()) {
            log.warn("抽奖责任链-权重规则解析为空，直接放行. strategyId: {}", strategyId);
            return nextLogic(userId, strategyId);
        }

        // 3. 获取用户累计抽奖次数
        Integer userRaffleCount = strategyRepository.queryTotalUserRaffleCount(userId, strategyId);

        // 4. 寻找匹配档位 (寻找配置中 <= 用户当前抽奖次数的最大阈值)
        Long matchedKey = ruleValueMap
                .keySet()
                .stream()
                .filter(key -> userRaffleCount >= key)
                .max(Long::compare)
                .orElse(null);

        // 5. 判定匹配结果：若命中则截断责任链后续流程，直接产出奖品
        if (matchedKey != null) {
            Integer awardId = strategyDispatch.getRandomAwardId(strategyId, ruleValueMap.get(matchedKey));
            log.info("抽奖责任链-权重匹配成功 userId: {}, strategyId: {}, 命中档位: {}, 产出奖品ID: {}", userId, strategyId, matchedKey,
                    awardId);
            return DefaultChainFactory.StrategyAwardVO
                    .builder()
                    .awardId(awardId)
                    .logicModel(ruleModel())
                    .build();
        }

        // 6. 边界处理：用户抽奖次数未达到任何权重门槛，放行流转至后续节点（如默认抽奖节点）
        log.info("抽奖责任链-权重放行（累计抽奖次数未达标） userId: {}, strategyId: {}, 累计次数: {}", userId, strategyId, userRaffleCount);
        return nextLogic(userId, strategyId);
    }

    /**
     * 封装下一节点逻辑调用
     * 职责：增加非空校验，防止因责任链配置或装配问题导致 NPE 崩溃。
     */
    private DefaultChainFactory.StrategyAwardVO nextLogic(String userId, Long strategyId) {
        if (next() == null) {
            // 运行时防御：即使工厂封口失败或数据库缺失 default 配置，此处也仅打印错误而非崩溃
            log.error("【严重异常】责任链断裂，未发现后续有效节点（请检查策略 {} 是否包含 default 配置）", strategyId);
            return null;
        }
        return next().logic(userId, strategyId);
    }

    /**
     * 权重配置解析工具
     * 将 "4000:101,102" 格式解析为有序 Map
     */
    private Map<Long, String> getAnalyticalValue(String ruleValue) {
        if (StringUtils.isBlank(ruleValue)) return Collections.emptyMap();

        String[] ruleValueGroups = ruleValue.split(Constants.SPACE);
        Map<Long, String> ruleValueMap = new HashMap<>();

        for (String group : ruleValueGroups) {
            if (StringUtils.isBlank(group)) continue;
            String[] parts = group.split(Constants.COLON);
            if (parts.length != 2) continue;
            ruleValueMap.put(Long.parseLong(parts[0]), group);
        }
        return ruleValueMap;
    }

    @Override
    protected String ruleModel() {
        return DefaultChainFactory.LogicModel.RULE_WEIGHT.getCode();
    }
}