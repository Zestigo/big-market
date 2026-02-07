package com.c.domain.strategy.model.entity;

import com.c.types.common.Constants;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author cyh
 * @description 策略规则实体：用于存储具体的规则配置，如：积分权重、次数限制等
 * @date 2026/02/02
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StrategyRuleEntity {

    /** 抽奖策略ID */
    private Long strategyId;

    /** 奖品ID（若为通用策略规则，则此值为 null） */
    private Integer awardId;

    /** 规则类型；1-抽奖前配置、2-抽奖中配置、3-抽奖后配置 */
    private Integer ruleType;

    /** 规则模型标识；如：rule_weight（权重）、rule_lock（抽奖次数锁） */
    private String ruleModel;

    /** 规则配置值；例如权重规则格式："100:1,2,3 200:4,5,6" */
    private String ruleValue;

    /** 规则描述 */
    private String ruleDesc;

    /**
     * 获取权重规则配置分组映射
     * 描述：将配置字符串（如 "4000:101,102 5000:103,104"）解析为权重值与奖品 ID 集合的映射关系。
     * 逻辑：1.模型校验 -> 2.规则按组切分 -> 3.校验配置格式 -> 4.流式转换奖品列表 -> 5.构建映射表
     *
     * @return 权重映射表 (Key: 积分阈值, Value: 奖品 ID 列表)
     */
    public Map<String, List<Integer>> getRuleValueGroup() {
        // 1. 业务逻辑校验：仅针对权重规则模型(rule_weight)进行解析，且配置值不能为空
        if (!Constants.RULE_WEIGHT.equals(ruleModel) || StringUtils.isBlank(ruleValue)) {
            return Collections.emptyMap();
        }

        // 2. 规则切分：按照空格拆分出多个权重维度配置组
        String[] ruleGroups = ruleValue.split(Constants.SPACE);
        Map<String, List<Integer>> ruleValueGroups = new HashMap<>(ruleGroups.length);

        for (String rule : ruleGroups) {
            // 过滤空字符串，增强解析健壮性
            if (StringUtils.isBlank(rule)) continue;

            // 3. 键值对拆分：解析格式为 "积分阈值:奖品列表" 的子项 (示例: 4000:101,102)
            String[] parts = rule.split(Constants.COLON);
            if (parts.length != 2) {
                // 严谨校验：若格式不符合 [Key:Value]，抛出非法参数异常防止业务逻辑受损
                throw new IllegalArgumentException("strategy rule_weight configuration error, expected [key:value] " +
                        "but got " + rule);
            }

            // 4. 数据类型转换：利用 Stream 流将逗号分隔的 ID 字符串解析为 Integer 列表
            List<Integer> awardIds = Arrays
                    .stream(parts[1].split(Constants.SPLIT))
                    .filter(StringUtils::isNotBlank)
                    .map(Integer::parseInt)
                    .collect(Collectors.toList());

            // 5. 结果填充：以积分阈值 (parts[0]) 为 Key 存入映射表
            ruleValueGroups.put(parts[0], awardIds);
        }

        return ruleValueGroups;
    }

}