package com.c.test.domain.activity;

import com.c.domain.activity.model.entity.SkuRechargeEntity;
import com.c.domain.activity.service.IRaffleActivityAccountQuotaService;
import com.c.domain.activity.service.armory.IActivityArmory;
import com.c.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;
import java.util.concurrent.CountDownLatch;

/**
 * 抽奖活动账户额度服务测试
 * 核心验证：
 * 1. SKU 充值订单创建：验证从商品 SKU 到活动账户额度的转化逻辑。
 * 2. 幂等性控制：通过外部业务单号 (outBusinessNo) 确保充值操作不重复。
 * 3. 库存防超卖：验证在 Redis 预扣减模式下，数据库库存更新的准确性与一致性。
 *
 * @author cyh
 * @date 2026/02/01
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class RaffleActivityAccountQuotaServiceTest {

    @Resource
    private IRaffleActivityAccountQuotaService raffleActivityAccountQuotaService;

    @Resource
    private IActivityArmory activityArmory;

    /**
     * 测试前置准备：模拟系统初始化，执行活动军械库装配。
     * 将 SKU 库存及配置从数据库预热到缓存中，为高性能扣减做准备。
     */
    @Before
    public void setUp() {
        log.info("活动 SKU 预热装配中... SKU: 9011L, 结果: {}", activityArmory.assembleActivitySku(9011L));
    }

    /**
     * 测试：创建充值订单（幂等性验证）
     * 场景：使用相同的外部业务单号 (outBusinessNo) 进行二次充值。
     * 预期：数据库唯一索引 `uq_out_business_no` 生效，防止重复充值导致额度异常。
     */
    @Test
    public void test_createSkuRechargeOrder_duplicate() {
        SkuRechargeEntity skuRechargeEntity = new SkuRechargeEntity();
        skuRechargeEntity.setUserId("cyh");
        skuRechargeEntity.setSku(9011L);
        // 固定外部业务单号，测试第二次调用时的冲突拦截
        skuRechargeEntity.setOutBusinessNo("700091009119");

        String orderId = raffleActivityAccountQuotaService.createOrder(skuRechargeEntity);
        log.info("充值订单创建成功，订单号：{}", orderId);
    }

    /**
     * 测试：库存消耗流与数据最终一致性
     * 验证路径：
     * 1. 环境准备：确保数据库 SKU 库存设为 20，并清理 Redis 对应缓存。
     * 2. 执行过程：循环调用 20 次充值请求，观察缓存库存扣减。
     * 3. 结果观察：验证在高并发异步更新场景下，数据库物理库存最终是否精准清零。
     *
     *
     */
    @Test
    public void test_createSkuRechargeOrder() throws InterruptedException {
        for (int i = 0; i < 20; i++) {
            try {
                SkuRechargeEntity skuRechargeEntity = new SkuRechargeEntity();
                skuRechargeEntity.setUserId("cyh");
                skuRechargeEntity.setSku(9011L);
                // 模拟不同的交易单号，确保每笔充值请求的独立性
                skuRechargeEntity.setOutBusinessNo(RandomStringUtils.randomNumeric(12));

                String orderId = raffleActivityAccountQuotaService.createOrder(skuRechargeEntity);
                log.info("第 {} 次充值成功，订单号：{}", i + 1, orderId);
            } catch (AppException e) {
                // 当库存耗尽或校验失败时，捕获预期的业务异常
                log.warn("充值中断（预期内）：{}", e.getInfo());
            }
        }

        // 阻塞主线程，等待异步任务（如库存写回 Job）完成数据库同步，以便观察最终库表状态
        log.info("等待异步库存同步完成...");
        new CountDownLatch(1).await();
    }
}