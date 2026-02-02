package com.c.test.domain.activity;

import com.alibaba.fastjson.JSON;
import com.c.domain.activity.model.entity.PartakeRaffleActivityEntity;
import com.c.domain.activity.model.entity.UserRaffleOrderEntity;
import com.c.domain.activity.service.IRaffleActivityPartakeService;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;

/**
 * 抽奖活动参与服务集成测试
 * * 验证场景：
 * 1. 正常流：用户有额度且活动在有效期内，成功生成抽奖订单。
 * 2. 幂等流：多次调用接口，验证是否返回同一个未使用的 orderId。
 * 3. 异常流：额度不足、活动未开启等场景的异常抛出（需配合不同测试用例）。
 *
 * @author cyh
 * @date 2026/02/01
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class RaffleActivityPartakeServiceTest {

    @Resource
    private IRaffleActivityPartakeService raffleActivityPartakeService;

    /**
     * 测试：创建抽奖参与订单
     * * 逻辑验证重点：
     * - 校验是否正确触发了“总-月-日”多级账户的额度扣减。
     * - 校验生成的 UserRaffleOrder 实体属性（如 strategyId, state）是否符合活动配置。
     * - 观察日志中 orderId 的生成情况，确保唯一性或幂等性。
     */
    @Test
    public void test_createOrder() {
        // 1. 准备请求参数：指定测试用户与存量活动 SKU 关联的活动 ID
        PartakeRaffleActivityEntity partakeRaffleActivityEntity = new PartakeRaffleActivityEntity();
        partakeRaffleActivityEntity.setUserId("cyh");
        partakeRaffleActivityEntity.setActivityId(100301L);

        // 2. 调用参与活动核心接口
        // 此步涉及：DB 事务操作（账户扣减 + 订单写入）、Redis 预校验等。
        UserRaffleOrderEntity userRaffleOrder = raffleActivityPartakeService.createOrder(partakeRaffleActivityEntity);

        // 3. 输出执行结果，便于人工复核数据快照
        log.info("请求参数：{}", JSON.toJSONString(partakeRaffleActivityEntity));
        log.info("测试结果：{}", JSON.toJSONString(userRaffleOrder));

    }

}