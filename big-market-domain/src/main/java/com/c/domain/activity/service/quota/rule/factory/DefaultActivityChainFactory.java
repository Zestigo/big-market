package com.c.domain.activity.service.quota.rule.factory;

import com.c.domain.activity.service.quota.rule.IActionChain;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 抽奖活动规则责任链工厂
 * * 知识点点拨：
 * 1. 策略模式：通过 Map 注入，将所有 IActionChain 的实现类收集起来。
 * 2. 责任链模式：将离散的策略节点，按照业务逻辑首尾相连。
 *
 * @author cyh
 * @date 2026/02/17
 */
@Service
public class DefaultActivityChainFactory {

    private final IActionChain actionChain; /* 责任链头节点：整个链路的唯一入口 */

    /**
     * 构造函数：实现规则节点的自动发现与动态编排
     *
     * @param actionChainGroup Spring 会自动将所有 IActionChain 接口的实现类注入到这个 Map 中
     *                         Key: Bean 的名称（如 activity_base_action）
     *                         Value: 对应的实例对象
     */
    public DefaultActivityChainFactory(Map<String, IActionChain> actionChainGroup) {
        // 知识点 1：枚举的有序性
        // ActionModel.values() 返回的数组顺序，严格等同于你在枚举类中定义的先后顺序。
        ActionModel[] models = ActionModel.values();

        // 知识点 2：确定头节点
        // 责任链必须有一个起点。我们取枚举定义的第一个节点作为“第一道关卡”。
        this.actionChain = actionChainGroup.get(models[0].getCode());
        if (null == this.actionChain) {
            throw new RuntimeException("责任链初始化失败：未能找到头节点 [" + models[0].getCode() + "]");
        }

        // 知识点 3：动态挂载（指针移动）
        // 想象你在排队，current 始终代表当前队列的最后一个人，新来的人（nextNode）接在他后面。
        IActionChain current = this.actionChain; /* 初始指向头节点 */

        for (int i = 1; i < models.length; i++) {
            IActionChain nextNode = actionChainGroup.get(models[i].getCode());
            if (null != nextNode) {
                // 将新节点挂在当前节点的后面
                current.appendNext(nextNode);
                // 关键点：将 current 指针移向新挂载的节点，为下一次“接龙”做准备
                current = nextNode;
            }
        }
    }

    /**
     * 向外暴露链路入口
     * 业务层拿到这个 actionChain 后，只需调用其 action() 方法，
     * 内部会自动根据 appendNext 的顺序执行下去。
     */
    public IActionChain openActionChain() {
        return this.actionChain;
    }

    /**
     * 业务规则模型枚举
     * 【重要】这里的定义顺序 = 责任链的执行顺序
     */
    @Getter
    @AllArgsConstructor
    public enum ActionModel {

        // 第一步：基础校验（优先级最高）
        BASE_ACTION("activity_base_action", "活动状态、时间校验"),

        // 第二步：库存校验（基础通过后执行）
        SKU_STOCK_ACTION("activity_sku_stock_action", "库存预扣减校验");

        private final String code; /* 对应 Spring Bean 的 ID */
        private final String info; /* 描述 */
    }
}