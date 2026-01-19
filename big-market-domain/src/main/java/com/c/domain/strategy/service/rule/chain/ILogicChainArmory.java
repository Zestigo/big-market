package com.c.domain.strategy.service.rule.chain;

/**
 * @author cyh
 * @description 责任链装配工（军械库）接口
 * @details 该接口定义了责任链节点的串联与获取行为。
 * “装配”是责任链模式的核心步骤，通过该接口可以将分散的规则节点（如黑名单、权重、保底）按照业务优先级逻辑组装成完整的执行链条。
 * @date 2026/01/18
 */
public interface ILogicChainArmory {

    /**
     * 装配/追加后续节点
     *
     * @param next 下一个待挂载的逻辑链节点
     * @return 返回当前节点本身，以便支持链式调用（Fluent API 风格）进行连续装配
     * @details 将指定的逻辑节点挂载到当前节点的下游，形成链路。
     */
    ILogicChain appendNext(ILogicChain next);

    /**
     * 获取下游执行节点
     *
     * @return 下一个责任链节点实现对象
     * @details 在责任链执行过程中，用于获取当前节点之后需要跳转处理的逻辑节点。
     */
    ILogicChain next();
}