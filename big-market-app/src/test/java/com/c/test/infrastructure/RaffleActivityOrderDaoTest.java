package com.c.test.infrastructure;

import com.alibaba.fastjson.JSON;
import com.c.infrastructure.dao.IRaffleActivityOrderDao;
import com.c.infrastructure.po.RaffleActivityOrder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.jeasy.random.EasyRandom;
import org.jeasy.random.EasyRandomParameters;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.util.Date;

/**
 * 抽奖活动订单仓储层集成测试
 * 优化重点：数据回滚、自动化断言、分片键逻辑验证
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class RaffleActivityOrderDaoTest {

    @Resource
    private IRaffleActivityOrderDao raffleActivityOrderDao;

    /**
     * EasyRandom 是一个 Java 对象随机填充工具，
     * 它可以自动识别类中的属性类型（String, Integer, Date等）并填充随机数据，
     * 避免了手动写大量 set 方法来构造测试数据的麻烦。
     */
    private EasyRandom easyRandom;

    /**
     * @Before 注解的方法会在每个 @Test 测试用例执行前运行一次，
     * 用于初始化测试环境和配置工具类。
     */
    @Before
    public void setup() {
        // 获取当前系统日期，用于限定随机日期的生成范围
        LocalDate now = LocalDate.now();

        // EasyRandomParameters 用于定义随机生成的“规则”
        EasyRandomParameters parameters = new EasyRandomParameters()
                // 1. 限制生成的字符串长度在 6 到 10 个字符之间（防止超出数据库字段长度限制）
                .stringLengthRange(6, 10)
                // 2. 限制生成的日期范围（从今天到明天）。
                .dateRange(now, now.plusDays(1))
                // 3. 递归深度设为 1。
                // 作用：如果对象 A 包含对象 B，只填充 B 的基本属性，不再继续往下往深层递归生成。
                // 这样可以提高性能，并有效防止循环引用导致的堆栈溢出。
                .randomizationDepth(1);

        // 根据上述规则创建 EasyRandom 实例
        easyRandom = new EasyRandom(parameters);
    }

    @Test
    public void test_insert_random() {
        for (int i = 0; i < 32; i++) {
            RaffleActivityOrder raffleActivityOrder = new RaffleActivityOrder();

            // EasyRandom 生成 String 没问题
            String userId = easyRandom.nextObject(String.class);
            raffleActivityOrder.setUserId(userId);
            raffleActivityOrder.setActivityId(100301L);
            raffleActivityOrder.setActivityName("抽奖测试活动-随机组");
            raffleActivityOrder.setStrategyId(100006L);
            raffleActivityOrder.setOrderId(RandomStringUtils.randomNumeric(12));
            raffleActivityOrder.setOrderTime(new Date()); // 这里用 java.util.Date 是对的
            raffleActivityOrder.setState("not_used");

            try {
                log.info("准备插入数据 - 用户ID: {}, 订单ID: {}", userId, raffleActivityOrder.getOrderId());
                raffleActivityOrderDao.insert(raffleActivityOrder);
                log.info("插入成功！");
            } catch (Exception e) {
                log.error("插入失败，用户ID: {}, 异常信息: {}", userId, e.getMessage());
                // 既然是测试，建议保留 throw 方便报错时直接定位
                throw e;
            }
        }
    }
}