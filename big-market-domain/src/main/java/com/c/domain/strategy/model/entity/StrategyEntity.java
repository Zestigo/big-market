package com.c.domain.strategy.model.entity;

import com.c.types.common.Constants;
import lombok.*;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StrategyEntity {

    /** 策略ID (对应数据库 int(8)) */
    private Long strategyId;

    /** 策略描述 */
    private String strategyDesc;

    /** 规则模型，规则之间用英文逗号分隔 */
    private String ruleModels;

    /**
     * 获取规则模型数组
     * 优化点：
     * 1. 返回空数组而非 null，符合 Effective Java 编程建议，调用方无需判空。
     * 2. 使用 StringUtils.split 提高健壮性。
     */
    public String[] ruleModels() {
        if (StringUtils.isBlank(ruleModels)) {
            return new String[0];
        }
        return StringUtils.split(ruleModels, Constants.SPLIT);
    }

    /**
     * 获取权重规则模型
     * 优化点：使用 Stream 流简化代码，逻辑更清晰
     */
    public String getRuleWeight() {
        return Arrays.stream(this.ruleModels())
                     .filter("rule_weight"::equals)
                     .findFirst()
                     .orElse(null);
    }

    /**
     * 检查是否包含特定的规则模型
     * 这是一个典型的领域驱动方法，将判断逻辑内聚在实体内部
     */
    public boolean hasRuleModel(String ruleModelName) {
        if (StringUtils.isBlank(ruleModels)) return false;
        return Arrays.asList(this.ruleModels()).contains(ruleModelName);
    }
}