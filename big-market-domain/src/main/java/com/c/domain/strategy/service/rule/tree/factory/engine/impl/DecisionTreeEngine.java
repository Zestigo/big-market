package com.c.domain.strategy.service.rule.tree.factory.engine.impl;

import com.c.domain.strategy.model.vo.RuleLogicCheckTypeVO;
import com.c.domain.strategy.model.vo.RuleTreeNodeLineVO;
import com.c.domain.strategy.model.vo.RuleTreeNodeVO;
import com.c.domain.strategy.model.vo.RuleTreeVO;
import com.c.domain.strategy.service.rule.tree.ILogicTreeNode;
import com.c.domain.strategy.service.rule.tree.factory.DefaultTreeFactory;
import com.c.domain.strategy.service.rule.tree.factory.engine.IDecisionTreeEngine;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * 决策树引擎实现类
 * 核心逻辑：
 * 按照预定义的树形结构，从根节点开始执行逻辑，根据每个节点的执行结果（如：允许、接管等）
 * 匹配对应的连线（Line）跳转到下一个节点，直到没有后续节点为止，最终输出策略执行结果。
 *
 * @author cyh
 * @date 2026/01/19
 */
@Slf4j
public class DecisionTreeEngine implements IDecisionTreeEngine {

    private final Map<String, ILogicTreeNode> logicTreeNodeMap;
    private final RuleTreeVO ruleTreeVO;

    public DecisionTreeEngine(Map<String, ILogicTreeNode> logicTreeNodeGroup, RuleTreeVO ruleTreeVO) {
        this.logicTreeNodeMap = logicTreeNodeGroup;
        this.ruleTreeVO = ruleTreeVO;
    }

    @Override
    public DefaultTreeFactory.StrategyAwardVO process(String userId, Long strategyId, Integer awardId) {
        // 1. 获取起始节点
        String nextNode = ruleTreeVO.getTreeRootRuleNode();
        Map<String, RuleTreeNodeVO> treeNodeMap = ruleTreeVO.getTreeNodeMap();

        if (null == treeNodeMap || treeNodeMap.isEmpty()) {
            log.error("[决策树异常] 规则树ID: {} 节点配置为空，userId: {}", ruleTreeVO.getTreeId(), userId);
            return null;
        }

        log.info("[决策树开始] 规则树ID: {}, 用户ID: {}, 策略ID: {}, 初始节点: {}",
                ruleTreeVO.getTreeId(), userId, strategyId, nextNode);

        DefaultTreeFactory.StrategyAwardVO strategyAwardVO = null;

        // 3. 循环决策
        while (null != nextNode) {
            RuleTreeNodeVO ruleTreeNode = treeNodeMap.get(nextNode);
            if (null == ruleTreeNode) {
                log.warn("[决策树中断] 找不到节点配置: {}, 树ID: {}", nextNode, ruleTreeVO.getTreeId());
                break;
            }

            // 获取组件实现类
            ILogicTreeNode logicTreeNode = logicTreeNodeMap.get(ruleTreeNode.getRuleKey());
            String ruleValue = ruleTreeNode.getRuleValue();

            // 5. 执行业务逻辑
            DefaultTreeFactory.TreeActionEntity logicEntity = logicTreeNode.logic(userId, strategyId, awardId, ruleValue);
            RuleLogicCheckTypeVO checkType = logicEntity.getRuleLogicCheckType();
            strategyAwardVO = logicEntity.getStrategyAwardVO();

            // 【关键日志】打印当前节点执行后的状态
            log.info("[决策树节点] 树ID: {}, 节点: {}, 规则Key: {}, 决策结果: {}, 下一节点: {}",
                    ruleTreeVO.getTreeId(), nextNode, ruleTreeNode.getRuleKey(), checkType.getCode(),
                    nextNode(checkType.getCode(), ruleTreeNode.getTreeNodeLineVOList()));

            // 7. 路径寻优
            nextNode = nextNode(checkType.getCode(), ruleTreeNode.getTreeNodeLineVOList());
        }

        log.info("[决策树结束] 规则树ID: {}, 用户ID: {}, 最终奖品ID: {}",
                ruleTreeVO.getTreeId(), userId, (strategyAwardVO != null ? strategyAwardVO.getAwardId() : "无"));

        return strategyAwardVO;
    }

    public String nextNode(String matterValue, List<RuleTreeNodeLineVO> treeNodeLineVOList) {
        if (null == treeNodeLineVOList || treeNodeLineVOList.isEmpty()) {
            return null;
        }

        for (RuleTreeNodeLineVO nodeLine : treeNodeLineVOList) {
            if (decisionLogic(matterValue, nodeLine)) {
                return nodeLine.getRuleNodeTo();
            }
        }

        // 此处如果返回 null 且不是叶子节点，说明配置有误
        return null;
    }

    public boolean decisionLogic(String matterValue, RuleTreeNodeLineVO nodeLine) {
        switch (nodeLine.getRuleLimitType()) {
            case EQUAL:
                return matterValue.equals(nodeLine.getRuleLimitValue().getCode());
            case GT:
            case LT:
            case GE:
            case LE:
            default:
                log.warn("[决策树配置警报] 暂不支持的连线比较类型: {}, 树ID: {}",
                        nodeLine.getRuleLimitType(), ruleTreeVO.getTreeId());
                return false;
        }
    }
}