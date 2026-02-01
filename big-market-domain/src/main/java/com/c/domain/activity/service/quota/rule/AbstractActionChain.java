package com.c.domain.activity.service.quota.rule;

/**
 * 规则过滤链抽象基类
 * 该类实现了责任链的基础结构，封装了指向下一个执行节点的引用。
 * 所有的具体业务规则（如库存校验、黑名单校验等）应继承此类，
 * 并通过调用 {@link #next()} 获取后续流程。
 *
 * @author cyh
 * @date 2026/01/29
 */
public abstract class AbstractActionChain implements IActionChain {

    /**
     * 下一个业务规则执行节点
     */
    private IActionChain next;

    /**
     * 获取当前节点的后续执行链
     *
     * @return 具体的规则执行节点；若为 null 则代表当前节点为链路终点
     */
    @Override
    public IActionChain next() {
        return next;
    }

    /**
     * 装配链路：设置当前节点的下一个节点
     *
     * @param next 即将挂载的业务规则节点
     * @return 被挂载的 next 节点，支持流式链路组装
     */
    @Override
    public IActionChain appendNext(IActionChain next) {
        this.next = next;
        return next;
    }

}