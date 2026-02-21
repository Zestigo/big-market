package com.c.domain.strategy.service.rule.chain;

import com.c.domain.strategy.service.rule.chain.factory.DefaultChainFactory;

/**
 * 抽奖逻辑责任链抽象基类
 * 该类实现了责任链的基础结构，封装了链表指针（next）的维护逻辑。
 *
 * @author cyh
 * @date 2026/02/21
 */
public abstract class AbstractLogicChain implements ILogicChain {

    /* 下一个责任链节点 */
    private ILogicChain next;

    /**
     * 向当前节点后追加下一个执行节点
     *
     * @param next 下一个逻辑节点实现类
     * @return 返回传入的 next 节点，用于支持工厂类中的链式偏移
     */
    @Override
    public ILogicChain appendNext(ILogicChain next) {
        this.next = next;
        return next;
    }

    /**
     * 获取链条中的下一个节点
     */
    @Override
    public ILogicChain next() {
        return next;
    }

    /**
     * 责任链逻辑执行模板
     *
     * @param userId     用户ID
     * @param strategyId 策略ID
     * @return 抽奖奖品回调结果
     */
    @Override
    public abstract DefaultChainFactory.StrategyAwardVO logic(String userId, Long strategyId);

    /**
     * 获取当前逻辑节点关联的规则模型编码
     */
    protected abstract String ruleModel();
}