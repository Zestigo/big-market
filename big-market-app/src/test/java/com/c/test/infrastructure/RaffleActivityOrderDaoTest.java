package com.c.test.infrastructure;

import com.alibaba.fastjson.JSON;
import com.c.infrastructure.dao.IRaffleActivityOrderDao;
import com.c.infrastructure.po.RaffleActivityOrder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils; // 建议用 lang3
import org.jeasy.random.EasyRandom;
import org.jeasy.random.EasyRandomParameters;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;

/**
 * 抽奖活动订单仓储层集成测试
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class RaffleActivityOrderDaoTest {

    @Resource
    private IRaffleActivityOrderDao raffleActivityOrderDao;

    private EasyRandom easyRandom;

    @Before
    public void setup() {
        // 优化 EasyRandom 配置，确保生成的随机数据符合数据库长度和类型限制
        EasyRandomParameters parameters = new EasyRandomParameters().stringLengthRange(6, 10) // 限制字符串长度
                                                                    .randomizationDepth(1);
        easyRandom = new EasyRandom(parameters);
    }

    /**
     * 测试用例：批量模拟随机用户数据插入
     * 解决“routing to multiple data nodes”的关键：确保 userId 的生成符合分片算法预期
     */
    @Test
    public void test_insert_random() {
        for (int i = 0; i < 32; i++) {
            RaffleActivityOrder raffleActivityOrder = new RaffleActivityOrder();

            String userId = easyRandom.nextObject(String.class);
            raffleActivityOrder.setUserId(userId);
            raffleActivityOrder.setActivityId(100301L);
            raffleActivityOrder.setActivityName("抽奖测试活动-随机组");
            raffleActivityOrder.setStrategyId(100006L);
            raffleActivityOrder.setOrderId(RandomStringUtils.randomNumeric(12));
            raffleActivityOrder.setOrderTime(new Date());
            raffleActivityOrder.setState("not_used");

            try {
                log.info("准备插入数据 - 用户ID: {}, 订单ID: {}", userId, raffleActivityOrder.getOrderId());
                raffleActivityOrderDao.insert(raffleActivityOrder);
                log.info("插入成功！");
            } catch (Exception e) {
                log.error("插入失败，用户ID: {}, 异常信息: {}", userId, e.getMessage());
                throw e; // 抛出异常使测试失败
            }
        }
    }

    /**
     * 测试用例：特定用户固定数据插入
     */
    @Test
    public void test_insert() {
        RaffleActivityOrder raffleActivityOrder = new RaffleActivityOrder();
        raffleActivityOrder.setUserId("user_001"); // 建议使用带业务语义的 ID
        raffleActivityOrder.setActivityId(100301L);
        raffleActivityOrder.setActivityName("抽奖测试活动-固定组");
        raffleActivityOrder.setStrategyId(100006L);
        raffleActivityOrder.setOrderId(RandomStringUtils.randomNumeric(12));
        raffleActivityOrder.setOrderTime(new Date());
        raffleActivityOrder.setState("not_used");

        raffleActivityOrderDao.insert(raffleActivityOrder);
        log.info("固定用户数据插入完成");
    }

    /**
     * 测试用例：查询订单列表
     */
    @Test
    public void test_queryRaffleActivityOrderByUserId() {
        String userId = "user_001";
        List<RaffleActivityOrder> results = raffleActivityOrderDao.queryRaffleActivityOrderByUserId(userId);

        log.info("查询用户 {} 的订单，数量: {}", userId, results.size());
        if (!results.isEmpty()) {
            log.info("首条订单详情: {}", JSON.toJSONString(results.get(0)));
        }
    }
}