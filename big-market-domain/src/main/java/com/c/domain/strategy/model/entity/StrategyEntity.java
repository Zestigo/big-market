package com.c.domain.strategy.model.entity;

import com.c.types.common.Constants;
import lombok.*;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;

/**
 * @description 抽奖策略实体：封装了策略配置及其关联的规则引擎模型。
 * @author cyh
 * @date 2026/02/02
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StrategyEntity {

    /** 策略唯一标识 */
    private Long strategyId;

    /** 策略描述：简述活动背景或策略用途 */
    private String strategyDesc;

    /** 抽奖规则模型集合：以逗号分隔的字符串（如：rule_weight,rule_lock,rule_luck_award） */
    private String ruleModels;

    /* -------------------------------- 领域行为方法 (Domain Logic) -------------------------------- */

    /**
     * 解析规则模型列表
     * @return 规则模型数组。若无配置则返回空数组，确保调用方循环时不触发 NPE。
     */
    public String[] ruleModels() {
        if (StringUtils.isBlank(ruleModels)) {
            return new String[0];
        }
        return StringUtils.split(ruleModels, Constants.SPLIT);
    }

    /**
     * 获取权重规则模型标识
     * @return 若包含权重规则则返回 "rule_weight"，否则返回 null。用于后续匹配权重抽奖方案。
     */
    public String getRuleWeight() {
        return Arrays.stream(this.ruleModels())
                     .filter("rule_weight"::equals)
                     .findFirst()
                     .orElse(null);
    }

    /**
     * 业务断言：判断当前策略是否配置了指定的规则模型
     * @param ruleModelName 规则名称
     * @return 包含返回 true
     */
    public boolean hasRuleModel(String ruleModelName) {
        if (StringUtils.isBlank(ruleModels)) return false;
        // 注意：Arrays.asList 在高频调用下可考虑在实体内缓存 List 提高性能
        return Arrays.asList(this.ruleModels()).contains(ruleModelName);
    }
}