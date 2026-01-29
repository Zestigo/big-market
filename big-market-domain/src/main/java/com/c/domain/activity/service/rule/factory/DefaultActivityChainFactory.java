package com.c.domain.activity.service.rule.factory;

import com.c.domain.activity.service.rule.IActionChain;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 抽奖活动规则责任链工厂
 * 职责：
 * 1. 自动扫描并收集 Spring 容器中所有的 {@link IActionChain} 策略实现。
 * 2. 按照业务预设的逻辑顺序（准入校验 -> 异步库存），完成责任链的装配。
 * 3. 向外层服务（应用层/领域逻辑）提供统一的链路执行入口。
 *
 * @author cyh
 * @date 2026/01/29
 */
@Service
public class DefaultActivityChainFactory {

    /** 责任链头节点，负责开启整个校验流程 */
    private final IActionChain actionChain;

    /**
     * 构造函数：实现规则节点的自动发现与硬编码编排
     *
     * @param actionChainGroup Spring 自动注入。Key: Bean名称 (如 "activity_base_action"), Value: 实现类实例
     */
    public DefaultActivityChainFactory(Map<String, IActionChain> actionChainGroup) {
        // 1. 获取基础校验节点（第一道防线：状态、时间、静态库存）
        actionChain = actionChainGroup.get(ActionModel.activity_base_action.code);

        // 2. 编排责任链：将库存扣减节点（第二道防线：Redis 预扣、MQ发送）挂载到基础校验节点之后
        // 通过 ActionModel 枚举管理 Bean 名称，避免硬编码字符串带来的拼写错误
        actionChain.appendNext(actionChainGroup.get(ActionModel.activity_sku_stock_action.getCode()));
    }

    /**
     * 开启活动规则校验链路
     *
     * @return 已经组装完毕的规则链头节点，调用其 action 方法即可开始递归校验
     */
    public IActionChain openActionChain() {
        return this.actionChain;
    }

    /**
     * 活动动作模型枚举
     * 统一管理责任链节点的 Bean 名称与业务描述。
     * 当需要新增节点（如黑名单过滤、风险控制）时，在此枚举中定义并在构造函数中挂载即可。
     */
    @Getter
    @AllArgsConstructor
    public enum ActionModel {

        activity_base_action("activity_base_action", "活动状态、时间及基础静态库存校验"), activity_sku_stock_action(
                "activity_sku_stock_action", "活动SKU物理库存预扣减及异步同步"),
        ;

        private final String code;
        private final String info;
    }

}