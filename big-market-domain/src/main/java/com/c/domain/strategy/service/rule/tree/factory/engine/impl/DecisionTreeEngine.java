package com.c.domain.strategy.service.rule.tree.factory.engine.impl;

import com.c.domain.strategy.model.vo.RuleLogicCheckTypeVO;
import com.c.domain.strategy.model.vo.RuleTreeNodeLineVO;
import com.c.domain.strategy.model.vo.RuleTreeNodeVO;
import com.c.domain.strategy.model.vo.RuleTreeVO;
import com.c.domain.strategy.service.rule.tree.ILogicTreeNode;
import com.c.domain.strategy.service.rule.tree.factory.DefaultTreeFactory;
import com.c.domain.strategy.service.rule.tree.factory.engine.IDecisionTreeEngine;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 决策树引擎实现类 (Decision Tree Execution Engine)
 * 职责：负责解析并驱动规则树的拓扑结构，根据节点逻辑返回值进行路径寻优。
 * 核心机制：
 * 1. 状态流转：基于 While 循环模拟有向无环图 (DAG) 的遍历。
 * 2. 节点解耦：逻辑节点 (ILogicTreeNode) 仅关注业务判定，引擎关注节点间的跳转逻辑。
 * 3. 结果产出：直至触达没有后继连线的叶子节点，或被中间节点“接管”直接返回结果。
 *
 * @author cyh
 * @since 2026/01/19
 */
@Slf4j
public class DecisionTreeEngine implements IDecisionTreeEngine {

    /** 逻辑节点组件池：Key 为规则 Key (如 rule_stock)，Value 为具体的业务逻辑实现 */
    private final Map<String, ILogicTreeNode> logicTreeNodeMap;

    /** 规则树视图：包含树结构、节点详情、连线规则 */
    private final RuleTreeVO ruleTreeVO;

    public DecisionTreeEngine(Map<String, ILogicTreeNode> logicTreeNodeGroup, RuleTreeVO ruleTreeVO) {
        this.logicTreeNodeMap = logicTreeNodeGroup;
        this.ruleTreeVO = ruleTreeVO;
    }

    /**
     * 驱动决策树执行
     * 流程：获取根节点 -> 执行节点逻辑 -> 匹配连线 -> 跳转下一节点 -> 产出最终奖品。
     */
    @Override
    public DefaultTreeFactory.StrategyAwardVO process(String userId, Long strategyId, Integer awardId,
                                                      Date endDateTime) {
        // 1. 获取决策树入口：根节点 ID
        String nextNode = ruleTreeVO.getTreeRootRuleNode();
        Map<String, RuleTreeNodeVO> treeNodeMap = ruleTreeVO.getTreeNodeMap();

        if (null == treeNodeMap || treeNodeMap.isEmpty()) {
            log.error("[决策树异常] 规则树 ID: {} 节点配置缺失，无法执行", ruleTreeVO.getTreeId());
            return null;
        }

        log.info("[决策树启动] 树ID: {}, 用户: {}, 初始节点: {}", ruleTreeVO.getTreeId(), userId, nextNode);

        DefaultTreeFactory.StrategyAwardVO strategyAwardVO = null;

        // 2. 循环决策：只要存在“下一跳”节点，就持续执行
        while (null != nextNode) {
            // 获取当前节点的元数据配置（包含规则 Key 和规则值）
            RuleTreeNodeVO ruleTreeNode = treeNodeMap.get(nextNode);
            if (null == ruleTreeNode) {
                log.warn("[决策树中断] 路径寻优失败，未找到节点: {}", nextNode);
                break;
            }

            // 获取对应的业务逻辑组件 (如库存扣减节点、次数校验节点)
            ILogicTreeNode logicTreeNode = logicTreeNodeMap.get(ruleTreeNode.getRuleKey());
            String ruleValue = ruleTreeNode.getRuleValue();

            // 执行节点业务逻辑
            DefaultTreeFactory.TreeActionEntity logicEntity = logicTreeNode.logic(userId, strategyId, awardId,
                    ruleValue, endDateTime);
            RuleLogicCheckTypeVO checkType = logicEntity.getRuleLogicCheckType();
            strategyAwardVO = logicEntity.getStrategyAwardVO();

            log.info("[决策树节点执行] 节点: {}, 规则: {}, 决策状态: {}", nextNode, ruleTreeNode.getRuleKey(), checkType.getInfo());

            // 3. 路径寻优：根据当前逻辑执行结果（放行/接管），匹配下一跳节点 ID
            nextNode = nextNode(checkType.getCode(), ruleTreeNode.getTreeNodeLineVOList());
        }

        log.info("[决策树完结] 树ID: {}, 用户: {}, 最终判定奖品: {}", ruleTreeVO.getTreeId(), userId, (strategyAwardVO != null ?
                strategyAwardVO.getAwardId() : "未中奖/被拦截"));

        return strategyAwardVO;
    }

    /**
     * 路径寻优核心算法
     * 遍历当前节点的所有流出连线，匹配满足比较条件的连线目的地。
     *
     * @param matterValue        当前节点逻辑执行的返回值 (ALLOW / TAKE_OVER)
     * @param treeNodeLineVOList 节点关联的连线集合
     * @return 下一跳转节点的 ID，若无可匹配路径则返回 null（代表触达叶子节点）
     */
    public String nextNode(String matterValue, List<RuleTreeNodeLineVO> treeNodeLineVOList) {
        if (null == treeNodeLineVOList || treeNodeLineVOList.isEmpty()) {
            return null;
        }

        for (RuleTreeNodeLineVO nodeLine : treeNodeLineVOList) {
            // 判定当前连线条件是否成立（如：返回值 == EQUAL）
            if (decisionLogic(matterValue, nodeLine)) {
                return nodeLine.getRuleNodeTo();
            }
        }
        return null;
    }

    /**
     * 连线过滤逻辑：支持多种比较运算符
     * 目前核心业务主要使用 EQUAL (等于) 进行路径匹配。
     */
    private boolean decisionLogic(String matterValue, RuleTreeNodeLineVO nodeLine) {
        switch (nodeLine.getRuleLimitType()) {
            case EQUAL:
                return matterValue.equals(nodeLine.getRuleLimitValue().getCode());
            case GT:
            case LT:
            case GE:
            case LE:
            default:
                log.warn("[决策树配置警报] 暂不支持非 EQUAL 类型的连线判定: {}", nodeLine.getRuleLimitType());
                return false;
        }
    }
}