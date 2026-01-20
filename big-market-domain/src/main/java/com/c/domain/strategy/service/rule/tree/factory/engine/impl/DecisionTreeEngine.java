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
 * 该引擎负责解析并执行配置好的规则树。通过递归或循环遍历树节点，根据节点间的连线逻辑（Line）
 * 最终计算出用户在当前策略下应获得的奖励或执行的结果。
 *
 * @author cyh
 * @date 2026/01/19
 */
@Slf4j
public class DecisionTreeEngine implements IDecisionTreeEngine {

    /**
     * 逻辑节点实现组：Spring自动注入所有ILogicTreeNode的实现类
     * Key=组件名（如@Component("rule_lock")的"rule_lock"），Value=具体节点实例
     * 设计意图：通过Map快速根据ruleKey找到对应的节点实现，时间复杂度O(1)
     */
    private final Map<String, ILogicTreeNode> logicTreeNodeMap;

    /**
     * 规则树配置对象：包含整棵树的结构、节点、连线信息
     * 设计意图：将规则树的结构与引擎逻辑解耦，支持动态配置
     */
    private final RuleTreeVO ruleTreeVO;

    /**
     * 构造器：注入依赖（节点Map+规则树配置）
     * 设计意图：通过构造器注入，保证依赖不可变（final），线程安全
     */
    public DecisionTreeEngine(Map<String, ILogicTreeNode> logicTreeNodeGroup, RuleTreeVO ruleTreeVO) {
        this.logicTreeNodeMap = logicTreeNodeGroup;
        this.ruleTreeVO = ruleTreeVO;
    }

    /**
     * 决策流程执行
     *
     * @param userId     用户ID
     * @param strategyId 策略ID
     * @param awardId    奖品ID
     * @return 最终决策出的奖品数据实体
     */
    @Override
    public DefaultTreeFactory.StrategyAwardData process(String userId, Long strategyId, Integer awardId) {
        DefaultTreeFactory.StrategyAwardData strategyAwardData = null;

        // 1. 获取规则树结构与根节点标识
        String nextNode = ruleTreeVO.getTreeRootRuleNode();
        Map<String, RuleTreeNodeVO> treeNodeMap = ruleTreeVO.getTreeNodeMap();

        // 2. 遍历规则树，直到到达叶子节点（nextNode 为空）
        RuleTreeNodeVO ruleTreeNode = treeNodeMap.get(nextNode);
        while (null != nextNode) {
            // 获取当前节点对应的逻辑处理单元
            ILogicTreeNode logicTreeNode = logicTreeNodeMap.get(ruleTreeNode.getRuleKey());

            // 3. 执行当前节点业务逻辑计算
            DefaultTreeFactory.TreeActionEntity logicEntity = logicTreeNode.logic(userId, strategyId, awardId);
            RuleLogicCheckTypeVO ruleLogicCheckTypeVO = logicEntity.getRuleLogicCheckType();
            strategyAwardData = logicEntity.getStrategyAwardData();

            log.info("决策树引擎【{}】treeId:{} node:{} code:{}", ruleTreeVO.getTreeName(), ruleTreeVO.getTreeId(), nextNode,
                    ruleLogicCheckTypeVO.getCode());

            // 4. 根据当前逻辑节点的处理结果，寻找下一跳节点
            nextNode = nextNode(ruleLogicCheckTypeVO.getCode(), ruleTreeNode.getTreeNodeLineVOList());
            ruleTreeNode = treeNodeMap.get(nextNode);
        }

        return strategyAwardData;
    }

    /**
     * 获取下一跳节点ID
     *
     * @param matterValue        当前节点决策出的结果值（如：ALLOW, TAKE_OVER）
     * @param treeNodeLineVOList 当前节点的所有出向连线集合
     * @return 下一个节点的Key，如果没有匹配项则返回 null（表示链路结束）
     */
    public String nextNode(String matterValue, List<RuleTreeNodeLineVO> treeNodeLineVOList) {
        if (null == treeNodeLineVOList || treeNodeLineVOList.isEmpty()) {
            return null;
        }

        for (RuleTreeNodeLineVO nodeLine : treeNodeLineVOList) {
            if (decisionLogic(matterValue, nodeLine)) {
                return nodeLine.getRuleNodeTo();
            }
        }

        // 如果有连线配置但没有任何一条线匹配成功，属于配置异常或逻辑闭环缺失
        throw new RuntimeException("决策树引擎，nextNode 计算失败，未找到可执行节点！路径匹配值：" + matterValue);
    }

    /**
     * 规则连线过滤逻辑比较
     * * @param matterValue 业务逻辑节点输出的特征值
     *
     * @param nodeLine 规则树连线配置（包含限定类型和限定值）
     * @return 是否走该连线
     */
    public boolean decisionLogic(String matterValue, RuleTreeNodeLineVO nodeLine) {
        switch (nodeLine.getRuleLimitType()) {
            case EQUAL:
                return matterValue.equals(nodeLine.getRuleLimitValue().getCode());

            // TODO: 待扩展实现以下逻辑运算符。目前默认仅支持相等判定。
            // 建议：后续可引入策略模式或数值比较工具类处理 GT, LT 等逻辑
            case GT:
            case LT:
            case GE:
            case LE:
            default:
                log.warn("决策树引擎：暂不支持的比较类型 {}", nodeLine.getRuleLimitType());
                return false;
        }
    }

}