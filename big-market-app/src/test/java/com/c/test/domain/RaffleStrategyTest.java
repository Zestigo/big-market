package com.c.test.domain;

import com.alibaba.fastjson.JSON;
import com.c.domain.strategy.model.entity.RaffleAwardEntity;
import com.c.domain.strategy.model.entity.RaffleFactorEntity;
import com.c.domain.strategy.service.IRaffleStrategy;
import com.c.domain.strategy.service.armory.IStrategyArmory;
import com.c.domain.strategy.service.rule.impl.RuleWeightLogicFilter;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.util.ReflectionTestUtils;

import javax.annotation.Resource;

/**
 * @description 抽奖策略领域测试
 * 重点测试：前置规则过滤（黑名单、权重）、常规抽奖算法、奖池库存消耗等逻辑。
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class RaffleStrategyTest {
    @Resource
    private IRaffleStrategy raffleStrategy;

    @Resource
    private RuleWeightLogicFilter ruleWeightLogicFilter;


    @Resource
    private IStrategyArmory strategyArmory;

    @Before
    public void setUp() {
        // 1. 核心修复：执行策略装配。将 100001L 策略的奖品概率表预热到 Redis
        // 否则后续在执行 getRandomAwardId 时，由于 Redis 没数据，getRateRange 会返回 null 并导致 NPE
        boolean success = strategyArmory.assembleLotteryStrategy(100001L);
        log.info("策略装配结果：{}", success);

        // 2. 模拟权重分值
        ReflectionTestUtils.setField(ruleWeightLogicFilter, "userScore", 4500L);
    }

    /**
     * 测试：常规抽奖逻辑
     * 验证：当用户不是黑名单且权重分值符合要求时，是否能正常根据概率获取奖品。
     */
    @Test
    public void test_performRaffle() {
        // 1. 构建抽奖因子：指定用户 ID 和 抽奖策略 ID
        RaffleFactorEntity raffleFactorEntity = RaffleFactorEntity.builder().userId("CYH").strategyId(100001L).build();

        // 2. 执行抽奖决策
        RaffleAwardEntity raffleAwardEntity = raffleStrategy.performRaffle(raffleFactorEntity);

        // 3. 输出结果日志，观察返回的 awardId 和 awardConfig 是否符合预期
        log.info("【标准抽奖测试】请求参数：{}", JSON.toJSONString(raffleFactorEntity));
        log.info("【标准抽奖测试】测试结果：{}", JSON.toJSONString(raffleAwardEntity));
    }

    /**
     * 测试：黑名单拦截逻辑
     * 场景：user003 是数据库中硬编码或配置在 rule_blacklist 中的用户。
     * 预期：触发 RuleActionEntity.TAKE_OVER（接管），直接返回黑名单指定的固定奖品（如：0积分或小礼品），
     * 且不会进入后续的随机概率抽奖。
     */
    @Test
    public void test_performRaffle_blacklist() {
        // 1. 构建黑名单用户因子
        RaffleFactorEntity raffleFactorEntity = RaffleFactorEntity.builder().userId("user003").strategyId(100001L).build();

        // 2. 执行抽奖：此时内部 doCheckRaffleBeforeLogic 应该在第一步就命中黑名单过滤器并直接返回
        RaffleAwardEntity raffleAwardEntity = raffleStrategy.performRaffle(raffleFactorEntity);

        log.info("【黑名单拦截测试】请求参数：{}", JSON.toJSONString(raffleFactorEntity));
        log.info("【黑名单拦截测试】测试结果：{}", JSON.toJSONString(raffleAwardEntity));
    }
}