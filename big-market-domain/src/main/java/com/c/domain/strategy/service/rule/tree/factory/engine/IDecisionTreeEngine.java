package com.c.domain.strategy.service.rule.tree.factory.engine;

import com.c.domain.strategy.service.rule.tree.factory.DefaultTreeFactory;

import java.util.Date;

/**
 * 决策树执行引擎接口
 * 职责：作为规则引擎的调度核心，负责驱动决策树（Decision Tree）的节点流转。
 * 逻辑：从根节点（Root）开始，根据各节点的逻辑判断结果（True/False/TakeOver），沿着既定路径（Edge）
 * 递归或循环执行，直至触达叶子节点（Leaf）并返回最终的决策指令。
 *
 * @author cyh
 * @since 2026/01/19
 */
public interface IDecisionTreeEngine {

    /**
     * 驱动决策链路计算
     * 业务过程：
     * 1. 节点匹配：根据 userId 和 strategyId 定位到具体的规则树模型。
     * 2. 逻辑寻址：以 awardId 为初始锚点，依次通过各节点（如：库存节点、次数锁节点、黑名单节点）。
     * 3. 结果裁定：综合各节点的判断反馈，产出最终可执行的奖品发放指令或拦截指令。
     *
     * @param userId      用户唯一标识。用于节点内的频控校验、身份识别及行为记录。
     * @param strategyId  策略配置 ID。用于加载对应的规则拓扑结构图。
     * @param awardId     初始预测奖品 ID。作为决策的输入原型，引擎将验证该奖品是否满足发放门槛。
     * @param endDateTime 活动截止时间。用于时效性校验节点，判断当前决策是否在合法活动周期内。
     * @return {@link DefaultTreeFactory.StrategyAwardVO} 决策结论。包含最终判定的奖品 ID 及其附加的业务配置值（RuleValue）。
     * @throws RuntimeException 当遇到节点配置缺失、逻辑环路或无法解析的路径时，应抛出异常以防止非法决策。
     */
    DefaultTreeFactory.StrategyAwardVO process(String userId, Long strategyId, Integer awardId, Date endDateTime);

}