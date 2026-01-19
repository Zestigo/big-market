package com.c.domain.strategy.service.rule.chain;

/**
 * 抽奖逻辑责任链抽象基类
 * 该类实现了责任链的基础结构，封装了链表指针（next）的维护逻辑。
 * 所有的具体逻辑节点（如：黑名单校验、权重校验、默认抽奖）均需继承此类。
 *
 * @author cyh
 * @date 2026/01/18
 */
public abstract class AbstractLogicChain implements ILogicChain {

    /**
     * 下一个责任链节点
     */
    private ILogicChain next;

    /**
     * 向当前节点后追加下一个执行节点
     *
     * @param next 下一个逻辑节点实现类
     * @return 刚添加的 next 节点，以便于链式调用（e.g. chain.appendNext(a).appendNext(b)）
     */
    @Override
    public ILogicChain appendNext(ILogicChain next) {
        this.next = next;
        return next;
    }

    /**
     * 获取链条中的下一个节点
     *
     * @return 下一个责任链逻辑节点
     */
    @Override
    public ILogicChain next() {
        return next;
    }

    protected abstract String ruleModel();
}