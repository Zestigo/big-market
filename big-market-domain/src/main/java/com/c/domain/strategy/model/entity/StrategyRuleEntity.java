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
     * 解析权重规则配置 (rule_weight)
     * 设计意图：
     * 1. 结构化解析：将非结构化的字符串 ruleValue 转换为业务易用的 Map 结构。
     * 2. 格式示例：配置 "4000:101,102 5000:103"，表示 4000 积分可抽 101/102 奖品。
     *
     * @return 权重分值 -> 对应的奖品ID集合。若非权重规则或格式非法，返回空 Map。
     */
    public Map<String, List<Integer>> getRuleValueGroup() {
        // 1. 业务逻辑校验：仅针对 rule_weight 模型进行解析
        if (!Constants.RULE_WEIGHT.equals(ruleModel) || StringUtils.isBlank(ruleValue)) {
            return Collections.emptyMap();
        }

        // 2. 规则切分：按空格拆分多个权重维度
        String[] ruleGroups = ruleValue.split(Constants.SPACE);
        Map<String, List<Integer>> ruleValueGroups = new HashMap<>(ruleGroups.length);

        for (String rule : ruleGroups) {
            if (StringUtils.isBlank(rule)) continue;

            // 3. 键值拆分：解析 "积分阈值:奖品列表"
            String[] parts = rule.split(Constants.COLON);
            if (parts.length != 2) {
                // 抛出异常或记录日志，防止因配置错误导致线上抽奖逻辑异常
                throw new IllegalArgumentException("strategy rule_weight configuration error, expected " +
                        "[key:value] but got " + rule);
            }

            // 4. 数据转换：将逗号分隔的 ID 字符串转为 List<Integer>
            List<Integer> awardIds = Arrays.stream(parts[1].split(Constants.SPLIT))
                                           .filter(StringUtils::isNotBlank).map(Integer::parseInt)
                                           .collect(Collectors.toList());

            // 【修正逻辑】此处应存入 parts[0] (积分值)，而非原始的 rule 字符串
            ruleValueGroups.put(parts[0], awardIds);
        }

        return ruleValueGroups;
    }
}