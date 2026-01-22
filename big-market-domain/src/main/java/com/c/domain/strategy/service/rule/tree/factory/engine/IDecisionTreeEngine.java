package com.c.domain.strategy.service.rule.tree.factory.engine;

import com.c.domain.strategy.service.rule.tree.factory.DefaultTreeFactory;

/**
 * 决策树引擎接口
 * 规则引擎的核心执行契约。负责驱动决策树的流转，从根节点开始，根据业务逻辑判定结果选择路径，
 * 直到得出最终的决策结论（如：发奖、拦截、降级等）。
 *
 * @author cyh
 * @date 2026/01/19
 */
public interface IDecisionTreeEngine {

    /**
     * 执行决策链路计算
     * 通过输入的业务维度参数（人、策、物），在决策树模型中进行逻辑寻址。
     *
     * @param userId     用户唯一标识，用于规则节点中的身份校验、次数过滤、人群匹配等业务判断
     * @param strategyId 策略配置ID，用于关联具体的抽奖策略或业务规则集
     * @param awardId    初始奖品ID，作为决策树处理的基准目标（如判断该奖品库存、限额等）
     * @return {@link DefaultTreeFactory.StrategyAwardVO} 决策结果，包含最终确定的奖品ID及对应的策略附加属性
     * @throws RuntimeException 当决策链路配置异常或节点逻辑计算无法匹配下一跳时，应抛出异常以保证流程安全性
     */
    DefaultTreeFactory.StrategyAwardVO process(String userId, Long strategyId, Integer awardId);
}