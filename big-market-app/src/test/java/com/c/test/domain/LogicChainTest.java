package com.c.test.domain;

import com.alibaba.fastjson.JSON;
import com.c.domain.strategy.service.armory.IStrategyArmory;
import com.c.domain.strategy.service.rule.chain.ILogicChain;
import com.c.domain.strategy.service.rule.chain.factory.DefaultChainFactory;
import com.c.domain.strategy.service.rule.chain.impl.RuleWeightLogicChain;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.util.ReflectionTestUtils;

import javax.annotation.Resource;

/**
 * **抽奖逻辑链（职责链模式）集成测试**
 * 本测试类用于验证在不同业务配置下，抽奖规则链条是否能正确流转。
 * 核心逻辑包含：黑名单过滤 -> 权重计算 -> 默认随机抽奖。
 *
 * @author cyh
 * @date 2026/01/20
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class LogicChainTest {

    @Resource
    private IStrategyArmory strategyArmory;
    @Resource
    private RuleWeightLogicChain ruleWeightLogicChain;
    @Resource
    private DefaultChainFactory defaultChainFactory;

    /**
     * **前置准备：策略装配**
     * 在执行测试用例前，将缓存（Redis）及内存中的策略数据初始化，
     * 模拟真实的抽奖环境装配过程。
     */
    @Before
    public void setUp() {
        log.info("策略装配开始...");
        strategyArmory.assembleLotteryStrategy(100001L);
        strategyArmory.assembleLotteryStrategy(100002L);
        strategyArmory.assembleLotteryStrategy(100003L);
        log.info("策略装配完成");
    }

    /**
     * **测试：黑名单规则拦截**
     * 场景：用户 ID 在策略配置的黑名单中。
     * 预期：逻辑链应在黑名单节点直接截断并返回配置的固定奖品，不再向下执行。
     */
    @Test
    public void test_LogicChain_rule_blacklist() {
        ILogicChain logicChain = defaultChainFactory.openLogicChain(100001L);
        DefaultChainFactory.StrategyAwardVO logic = logicChain.logic("user001", 100001L);
        log.info("黑名单测试结果，奖品信息：{}", JSON.toJSONString(logic));
    }

    /**
     * **测试：权重规则匹配**
     * 场景：用户积分满足特定权重阶梯（通过 Mock 模拟积分环境）。
     * 预期：逻辑链在权重节点根据用户积分范围进行精准抽奖，返回对应权重池的奖品。
     */
    @Test
    public void test_LogicChain_rule_weight() {
        // Mock 环境：注入用户积分为 4900，触发对应的权重奖品池逻辑
        ReflectionTestUtils.setField(ruleWeightLogicChain, "userScore", 4900L);

        ILogicChain logicChain = defaultChainFactory.openLogicChain(100001L);
        DefaultChainFactory.StrategyAwardVO logic = logicChain.logic("xiaofuge", 100001L);
        log.info("权重规则测试结果，奖品信息：{}", JSON.toJSONString(logic));
    }

    /**
     * **测试：默认兜底抽奖**
     * 场景：用户不满足黑名单、权重等任何前置置规则。
     * 预期：逻辑链流转至最后一环（Default），执行常规的概率算法抽奖。
     */
    @Test
    public void test_LogicChain_rule_default() {
        ILogicChain logicChain = defaultChainFactory.openLogicChain(100001L);
        DefaultChainFactory.StrategyAwardVO logic = logicChain.logic("xiaofuge", 100001L);
        log.info("默认抽奖测试结果，奖品信息：{}",JSON.toJSONString(logic));
    }
}