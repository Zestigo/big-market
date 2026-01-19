package com.c.domain.strategy.model.entity;

import com.c.types.common.Constants;
import lombok.*;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;

@Data // Lombok：自动生成get/set/toString等方法，减少模板代码
@Builder // 建造者模式：支持StrategyEntity.builder().strategyId(1L).build()创建对象
@AllArgsConstructor // 全参构造：适配复杂场景的实体创建
@NoArgsConstructor // 无参构造：适配Spring/ORM的反射创建
public class StrategyEntity {

    /** 策略ID (对应数据库 int(8)) */
    private Long strategyId;

    /** 策略描述 */
    private String strategyDesc;

    /** 规则模型，规则之间用英文逗号分隔 */
    private String ruleModels;

    /**
     * 获取规则模型数组
     * 设计意图：
     * 1. 封装规则模型的拆分逻辑，对外提供统一的数组接口，避免上层重复处理字符串拆分；
     * 2. 返回空数组而非null，符合Effective Java建议，调用方无需判空，减少NPE风险；
     * 3. 使用StringUtils.split提高健壮性，兼容空字符串/多分隔符场景。
     */
    public String[] ruleModels() {
        if (StringUtils.isBlank(ruleModels)) {
            return new String[0]; // 空数组兜底
        }
        return StringUtils.split(ruleModels, Constants.SPLIT); // 按逗号拆分
    }

    /**
     * 获取权重规则模型
     * 设计意图：
     * 1. 聚焦权重规则的查询逻辑，上层无需关心过滤细节；
     * 2. Stream流简化代码，相比循环更易读，符合函数式编程风格。
     */
    public String getRuleWeight() {
        return Arrays.stream(this.ruleModels()) // 转换为流
                     .filter("rule_weight"::equals) // 过滤权重规则
                     .findFirst() // 获取第一个匹配项
                     .orElse(null); // 无匹配返回null
    }

    /**
     * 检查是否包含特定的规则模型
     * 设计意图：
     * 1. DDD核心思想：将业务判断逻辑内聚在实体内部，而非散落在业务层；
     * 2. 对外提供语义化的方法（hasRuleModel），提升代码可读性。
     */
    public boolean hasRuleModel(String ruleModelName) {
        if (StringUtils.isBlank(ruleModels)) return false;
        return Arrays.asList(this.ruleModels()).contains(ruleModelName);
    }
}