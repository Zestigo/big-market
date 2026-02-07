package com.c.domain.strategy.model.vo;

import com.c.types.common.Constants;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 策略奖项规则值对象
 * 职责：负责承载并解析与特定奖品挂钩的规则模型配置。
 * 作用：将数据库中存储的逗号分隔字符串（如 "rule_lock,rule_luck_award"）转化为领域层可识别的规则列表。
 *
 * @author cyh
 * @date 2026/01/18
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StrategyAwardRuleModelVO {

    /** 原始规则模型配置：存储格式为逗号分隔的字符串 */
    private String ruleModels;

}