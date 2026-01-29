package com.c.domain.activity.service.rule;

/**
 * 抽奖活动规则链装配工厂接口
 * 该接口负责规则链的构建与维护，提供了链路节点的指向（next）和追加（append）能力。
 * 通过此接口，可以将不同的业务规则（如黑名单、库存、时间等）组装成一个有序的处理链路。
 *
 * @author cyh
 * @date 2026/01/29
 */
public interface IActionChainArmory {

    /**
     * 获取当前规则节点的下一个执行节点
     *
     * @return 下一个规则处理节点 {@link IActionChain}；若返回 null，则表示当前已是链路末端
     */
    IActionChain next();

    /**
     * 在当前链路末尾或指定位置追加下一个执行节点
     *
     * @param next 待接入的规则处理节点
     * @return 组装完成后的当前节点（或链路首节点），通常用于链式调用
     */
    IActionChain appendNext(IActionChain next);

}