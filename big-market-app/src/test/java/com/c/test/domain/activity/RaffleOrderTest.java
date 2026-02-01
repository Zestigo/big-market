package com.c.test.domain.activity;

import com.c.domain.activity.model.entity.SkuRechargeEntity;
import com.c.domain.activity.service.IRaffleOrder;
import com.c.domain.activity.service.rule.armory.IActivityArmory;
import com.c.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.RandomStringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;
import java.util.concurrent.CountDownLatch;

/**
 * @author cyh
 * @description 抽奖活动订单业务测试
 * 重点验证：SKU充值下单逻辑、Redis扣减库存、数据库最终一致性同步、业务幂等性。
 * @date 2026/01/27
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class RaffleOrderTest {

    @Resource
    private IRaffleOrder raffleOrder;
    @Resource
    private IActivityArmory activityArmory;

    @Before
    public void setUp() {
        log.info("装配活动：{}", activityArmory.assembleActivitySku(9011L));
    }

    /**
     * 高频下单及库存扣减压力测试
     * 1. Redis 预扣库存：观察 flushall 后，下单是否能正确初始化缓存并逐次扣减。
     * 2. 数据库最终一致性：观察 MQ 异步消息是否正常消费，使 DB 库存最终与缓存同步。
     * 3. 幂等性：验证 outBusinessNo 在数据库唯一索引约束下，是否能防止重复下单。
     * * 前置操作建议：
     * 1. 手动设置 raffle_activity_sku 表库存为 20。
     * 2. 执行 Redis 命令：flushall (清空缓存)。
     * 3. 启动测试，预期结果：20次下单成功，第21次开始抛出库存不足异常。
     */
    @Test
    public void test_createSkuRechargeOrder() throws InterruptedException {
        for (int i = 0; i < 10; i++) {
            try {
                SkuRechargeEntity skuRechargeEntity = new SkuRechargeEntity();
                skuRechargeEntity.setUserId("cyh");
                skuRechargeEntity.setSku(9011L);

                // 【幂等设计】outBusinessNo 代表外部业务单号（如充值流水号）
                // 数据库中设有 uq_out_business_no 唯一索引，若生成重复单号会触发 DuplicateKeyException。
                // 这里使用随机 12 位数模拟不同的业务请求。
                skuRechargeEntity.setOutBusinessNo(RandomStringUtils.randomNumeric(12));

                String orderId = raffleOrder.createSkuRechargeOrder(skuRechargeEntity);
                log.info("下单成功，订单号：{}，外部单号：{}", orderId, skuRechargeEntity.getOutBusinessNo());
            } catch (AppException e) {
                // 捕获预期的业务异常（如库存不足、活动未开启等）
                log.warn("下单失败（预期内异常）：{}", e.getInfo());
            } catch (Exception e) {
                // 捕获未知的系统异常
                log.error("下单失败（系统异常）：", e);
            }
        }

        // 阻塞主线程，防止测试进程过早结束导致 MQ 监听器（异步任务）还没来得及处理消息。
        log.info("测试主流程执行完毕，等待异步任务处理...");
        new CountDownLatch(1).await();
    }
}