package com.c.domain.strategy.model.entity;

import com.c.types.common.Constants;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StrategyRuleEntity {
    private Long strategyId;
    private Integer awardId;
    private Integer ruleType;
    private String ruleModel;
    private String ruleValue;
    private String ruleDesc;

    /**
     * 获取权重规则配置组
     * 场景：rule_weight 模式下，解析格式如 "100:1,2,3 200:4,5,6"
     *
     * @return Key: 积分阈值 (String) , Value: 奖品ID列表
     */
    public Map<String, List<Integer>> getRuleValueGroup() {
        // 1. 快速失败：非权重模型或值为空直接返回空 Map
        if (!Constants.RULE_WEIGHT.equals(ruleModel) || StringUtils.isBlank(ruleValue)) {
            return Collections.emptyMap();
        }

        // 2. 预设 HashMap 容量，减少扩容性能损耗
        String[] ruleGroups = ruleValue.split(Constants.SPACE);
        Map<String, List<Integer>> ruleValueGroups = new HashMap<>(ruleGroups.length, 1);

        for (String rule : ruleGroups) {
            if (StringUtils.isBlank(rule)) continue;

            // 3. 解析 "积分大小:奖品列表"
            String[] parts = rule.split(Constants.COLON);
            if (parts.length != 2) {
                throw new IllegalArgumentException("rule_weight invalid format. Expected [points:awardIds], but got: " + rule);
            }

            // 4. 使用 Stream 或循环转换奖品ID
            List<Integer> awardIds = Arrays.stream(parts[1].split(Constants.SPLIT)).filter(StringUtils::isNotBlank).map(Integer::parseInt)
                                           .collect(Collectors.toList());

            ruleValueGroups.put(rule, awardIds);
        }

        return ruleValueGroups;
    }
}
