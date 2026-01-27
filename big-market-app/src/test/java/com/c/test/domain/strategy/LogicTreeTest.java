package com.c.test.domain.strategy;

import com.alibaba.fastjson2.JSON;
import com.c.domain.strategy.model.vo.*;
import com.c.domain.strategy.service.rule.tree.factory.DefaultTreeFactory;
import com.c.domain.strategy.service.rule.tree.factory.engine.IDecisionTreeEngine;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * 决策树引擎功能测试
 * 模拟完整的规则树配置与执行链路：校验锁 -> 校验库存 -> 幸运奖兜底
 *
 * @author cyh
 * @date 2026/01/19
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class LogicTreeTest {

    @Resource
    private DefaultTreeFactory defaultTreeFactory;

    /**
     * 规则树链路配置模拟：
     * 1. [rule_lock] 规则锁节点 (根节点)
     * - TAKE_OVER (触发锁) -> [rule_luck_award] 幸运奖
     * - ALLOW (解锁成功) -> [rule_stock] 库存校验
     * 2. [rule_stock] 库存校验节点
     * - TAKE_OVER (库存不足) -> [rule_luck_award] 幸运奖
     * - ALLOW (库存充足) -> 链路终点 (返回库存节点结果)
     * 3. [rule_luck_award] 幸运奖节点 (叶子节点)
     */
    @Test
    public void test_tree_rule() {
        // 1. 构建 [规则锁] 节点及连线
        RuleTreeNodeVO rule_lock = RuleTreeNodeVO.builder().treeId(String.valueOf(100000001)).ruleKey("rule_lock").ruleDesc("抽奖次数锁判定")
                                                 .ruleValue("1").treeNodeLineVOList(new ArrayList<RuleTreeNodeLineVO>() {{
                    // 连线1：锁拦截 -> 转幸运奖
                    add(RuleTreeNodeLineVO.builder().treeId(String.valueOf(100000001)).ruleNodeFrom("rule_lock").ruleNodeTo("rule_luck_award")
                                          .ruleLimitType(RuleLimitTypeVO.EQUAL).ruleLimitValue(RuleLogicCheckTypeVO.TAKE_OVER)
                                          .build());

                    // 连线2：锁放行 -> 进库存校验
                    add(RuleTreeNodeLineVO.builder().treeId(String.valueOf(100000001)).ruleNodeFrom("rule_lock").ruleNodeTo("rule_stock")
                                          .ruleLimitType(RuleLimitTypeVO.EQUAL).ruleLimitValue(RuleLogicCheckTypeVO.ALLOW)
                                          .build());
                }}).build();

        // 2. 构建 [幸运奖] 节点 (最终接管节点，无出向连线)
        RuleTreeNodeVO rule_luck_award = RuleTreeNodeVO.builder().treeId(String.valueOf(100000001)).ruleKey("rule_luck_award")
                                                       .ruleDesc("兜底幸运奖励").ruleValue("1").treeNodeLineVOList(null).build();

        // 3. 构建 [库存规则] 节点及连线
        RuleTreeNodeVO rule_stock = RuleTreeNodeVO.builder().treeId(String.valueOf(100000001)).ruleKey("rule_stock").ruleDesc("库存扣减校验")
                                                  .ruleValue(null).treeNodeLineVOList(new ArrayList<RuleTreeNodeLineVO>() {{
                    // 连线1：库存不足 -> 转幸运奖
                    add(RuleTreeNodeLineVO.builder().treeId(String.valueOf(100000001)).ruleNodeFrom("rule_stock").ruleNodeTo("rule_luck_award")
                                          .ruleLimitType(RuleLimitTypeVO.EQUAL).ruleLimitValue(RuleLogicCheckTypeVO.TAKE_OVER)
                                          .build());
                }}).build();

        // 4. 组装规则树元数据 (RuleTreeVO)
        RuleTreeVO ruleTreeVO = new RuleTreeVO();
        ruleTreeVO.setTreeId(String.valueOf(100000001));
        ruleTreeVO.setTreeName("营销活动决策树");
        ruleTreeVO.setTreeDesc("用于处理抽奖过程中的次数锁、库存及兜底逻辑");
        ruleTreeVO.setTreeRootRuleNode("rule_lock"); // 设置根节点

        // 5. 将节点装载进树结构
            ruleTreeVO.setTreeNodeMap(new HashMap<String, RuleTreeNodeVO>() {{
                put("rule_lock", rule_lock);
                put("rule_stock", rule_stock);
                put("rule_luck_award", rule_luck_award);
        }});

        // 6. 开启引擎并执行测试
        IDecisionTreeEngine treeEngine = defaultTreeFactory.openLogicTree(ruleTreeVO);
        DefaultTreeFactory.StrategyAwardVO data = treeEngine.process("user_001", 100001L, 100);

        log.info("决策树执行完成，最终决策奖品：{}", JSON.toJSONString(data));
    }

}