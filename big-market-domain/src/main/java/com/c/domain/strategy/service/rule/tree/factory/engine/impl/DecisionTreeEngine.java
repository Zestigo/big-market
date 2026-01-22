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

    /**
     * 逻辑节点实现映射表：Key 为规则标识（如 rule_lock），Value 为对应的逻辑处理器实例。
     */
    private final Map<String, ILogicTreeNode> logicTreeNodeMap;

    /**
     * 规则树配置视图对象：存储了整棵树的拓扑结构（节点 Map 和根节点标识）。
     */
    private final RuleTreeVO ruleTreeVO;

    /**
     * 构造函数注入：确保引擎启动时已加载所有可用的逻辑节点和对应的树配置。
     */
    public DecisionTreeEngine(Map<String, ILogicTreeNode> logicTreeNodeGroup, RuleTreeVO ruleTreeVO) {
        this.logicTreeNodeMap = logicTreeNodeGroup;
        this.ruleTreeVO = ruleTreeVO;
    }

    /**
     * 决策树执行核心流程
     *
     * @param userId     用户ID
     * @param strategyId 策略ID
     * @param awardId    奖品ID
     * @return 最终决策出的奖品信息 VO（StrategyAwardVO）
     */
    @Override
    public DefaultTreeFactory.StrategyAwardVO process(String userId, Long strategyId, Integer awardId) {
        // 1. 获取树的起始位置（根节点 Key）
        String nextNode = ruleTreeVO.getTreeRootRuleNode();
        // 2. 获取当前树中所有的节点详细配置映射表
        Map<String, RuleTreeNodeVO> treeNodeMap = ruleTreeVO.getTreeNodeMap();

        // --- 容错与异常诊断逻辑：确保配置完整性 ---
        if (null == treeNodeMap || treeNodeMap.isEmpty()) {
            log.error("[决策树异常] 规则树ID: {} 对应的节点池为空，无法执行逻辑。", ruleTreeVO.getTreeId());
            return null;
        }

        // 用于记录并返回最终产出的奖励信息
        DefaultTreeFactory.StrategyAwardVO strategyAwardVO = null;

        // 3. 循环决策：只要存在下一个指向节点，就持续向下探测执行
        while (null != nextNode) {
            // 获取当前节点的配置信息
            RuleTreeNodeVO ruleTreeNode = treeNodeMap.get(nextNode);
            if (null == ruleTreeNode) break;

            // 4. 获取逻辑处理组件：根据配置的 ruleKey（如 rule_luck_award）从 Map 中定位具体的实现类
            ILogicTreeNode logicTreeNode = logicTreeNodeMap.get(ruleTreeNode.getRuleKey());
            // 获取当前节点配置的阈值/规则值（例如：限定抽奖10次必中的 "10"）
            String ruleValue = ruleTreeNode.getRuleValue();

            // 5. 执行具体的节点业务逻辑：如库存校验、黑名单拦截、抽奖次数判断等
            DefaultTreeFactory.TreeActionEntity logicEntity = logicTreeNode.logic(userId, strategyId,
                    awardId, ruleValue);

            // 6. 提取逻辑执行后的特征值（如：ALLOW 表示通过继续，TAKE_OVER 表示接管并结束）
            RuleLogicCheckTypeVO checkType = logicEntity.getRuleLogicCheckType();
            // 提取本次节点可能产出的奖励对象（中奖后此对象才有值）
            strategyAwardVO = logicEntity.getStrategyAwardVO();

            // 7. 路径寻优：根据当前节点执行结果的 Code 值和该节点的出向连线配置，寻找下一个节点 ID
            nextNode = nextNode(checkType.getCode(), ruleTreeNode.getTreeNodeLineVOList());
        }

        // 返回整棵决策树运行后的最终结果
        return strategyAwardVO;
    }

    /**
     * 根据当前决策结果寻找下一跳节点
     *
     * @param matterValue        当前节点的决策输出值（例如：0000代表通过，0001代表拦截）
     * @param treeNodeLineVOList 当前节点的出向连线集合（定义了什么结果该走哪条线）
     * @return 匹配成功的下一跳节点 ID。若没有匹配连线则返回 null，代表整棵树运行结束。
     */
    public String nextNode(String matterValue, List<RuleTreeNodeLineVO> treeNodeLineVOList) {
        // 如果当前节点没有配置出向连线，说明是叶子节点（终点）
        if (null == treeNodeLineVOList || treeNodeLineVOList.isEmpty()) {
            return null;
        }

        // 遍历所有连线配置，判断当前执行结果符合哪条连线的准入条件
        for (RuleTreeNodeLineVO nodeLine : treeNodeLineVOList) {
            if (decisionLogic(matterValue, nodeLine)) {
                // 返回符合条件的连线终点节点 ID
                return nodeLine.getRuleNodeTo();
            }
        }

        // 若配置了连线但根据当前结果找不到匹配路径，说明配置不完整或逻辑出现了非预期的闭环
        throw new RuntimeException("决策树配置异常：未找到匹配的路径连线。路径输出值：" + matterValue);
    }

    /**
     * 连线跳转逻辑判断
     *
     * @param matterValue 逻辑节点计算产出的实际结果值
     * @param nodeLine    连线上配置的准入限制（限定类型 + 限定值）
     * @return boolean    是否允许通过此连线
     */
    public boolean decisionLogic(String matterValue, RuleTreeNodeLineVO nodeLine) {
        // 根据连线配置的比较类型进行逻辑匹配
        switch (nodeLine.getRuleLimitType()) {
            case EQUAL:
                // 相等判定：节点的输出值必须等于连线要求的限定值（如：ALLOW == ALLOW）
                return matterValue.equals(nodeLine.getRuleLimitValue().getCode());

            // TODO：支持大于、小于、大于等于、小于等于等数值或类型比较
            case GT:
            case LT:
            case GE:
            case LE:
            default:
                log.warn("决策树引擎暂不支持的连线比较类型: {}。当前仅支持 EQUAL (相等) 判定。", nodeLine.getRuleLimitType());
                return false;
        }
    }

}