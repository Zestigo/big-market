package com.c.test.infrastructure;

import com.alibaba.fastjson2.JSON;
import com.c.infrastructure.dao.IRaffleActivityDao;
import com.c.infrastructure.po.RaffleActivity;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

/**
 * 抽奖活动DAO测试
 * 优化点：
 * 1. 引入 Assert 断言验证数据准确性
 * 2. 增加 @Transactional 保证测试后自动回滚（如果是增删改测试）
 * 3. 规范化日志输出格式
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class RaffleActivityDaoTest {

    @Resource
    private IRaffleActivityDao raffleActivityDao;

    @Test
    public void test_queryRaffleActivityByActivityId() {
        // 1. 准备数据
        Long activityId = 100301L;

        // 2. 执行查询
        RaffleActivity raffleActivity = raffleActivityDao.queryRaffleActivityByActivityId(activityId);

        // 3. 结构化日志输出（使用占位符，且仅在必要时序列化）
        log.info("查询抽奖活动详情 activityId: {} 结果: {}", activityId, JSON.toJSONString(raffleActivity));

        // 4. 关键断言（测试的核心：没有断言的测试只是脚本）
        Assert.assertNotNull("查询结果不应为空", raffleActivity);
        Assert.assertEquals("活动ID不匹配", activityId, raffleActivity.getActivityId());

        // 5. 业务状态校验（根据你的数据库实际预置数据来定）
        // Assert.assertEquals("活动状态应为开启", "OPEN", raffleActivity.getState());
    }

    /**
     * 建议：增加一个查询不存在 ID 的防御性测试
     */
    @Test
    public void test_queryRaffleActivityByActivityId_notFound() {
        RaffleActivity raffleActivity = raffleActivityDao.queryRaffleActivityByActivityId(-1L);
        Assert.assertNull("不存在的活动应返回 null", raffleActivity);
    }
}