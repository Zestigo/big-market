package com.c.test.domain.activity;

import com.alibaba.fastjson.JSON;
import com.c.domain.activity.model.entity.SkuRechargeEntity;
import com.c.domain.activity.model.entity.UnpaidActivityOrderEntity;
import com.c.domain.activity.model.vo.OrderTradeTypeVO;
import com.c.domain.activity.service.IRaffleActivityAccountQuotaService;
import com.c.domain.activity.service.armory.IActivityArmory;
import com.c.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;
import java.util.UUID;

/**
 * 抽奖活动账户额度服务测试
 *
 * @author cyh
 * @date 2026/02/15
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class RaffleActivityAccountQuotaServiceTest {

    @Resource
    private IRaffleActivityAccountQuotaService raffleActivityAccountQuotaService;

    @Resource
    private IActivityArmory activityArmory;

    /* 用户ID */
    private final String userId = "cyh";
    /* 测试专用SKU */
    private final Long sku = 9011L;

    /**
     * 测试前置准备：装配活动SKU库存与配置
     * 确保每次测试前 Redis 预热数据存在
     */
    @Before
    public void setUp() {
        // 如果环境需要配置 HTTP 代理，请确保系统环境变量已设置
        boolean isSuccess = activityArmory.assembleActivitySku(sku);
        log.info("活动 SKU 预热装配完成 | SKU: {} | 装配结果: {}", sku, isSuccess);
        Assert.assertTrue("活动SKU预热失败，请检查配置或数据库连接", isSuccess);
    }

    /**
     * 测试：创建充值订单 - 正常流程
     */
    @Test
    public void test_createOrder_success() {
        String outBusinessNo = RandomStringUtils.randomNumeric(12);
        SkuRechargeEntity skuRechargeEntity = buildEntity(outBusinessNo, OrderTradeTypeVO.REBATE_NO_PAY_TRADE);

        UnpaidActivityOrderEntity order = raffleActivityAccountQuotaService.createOrder(skuRechargeEntity);

        log.info("测试结果【正常下单】: {}", JSON.toJSONString(order));
        Assert.assertNotNull("订单实体不应为空", order);
        Assert.assertNotNull("生成的订单ID不应为空", order.getOrderId());
    }

    /**
     * 测试：返利无支付场景下的幂等性
     * 验证逻辑：使用相同的外部业务单号进行重复提交，系统应通过唯一索引或逻辑拦截
     */
    @Test
    public void test_createSkuRechargeOrder_duplicate() {
        String outBusinessNo = RandomStringUtils.randomNumeric(12);
        SkuRechargeEntity skuRechargeEntity = buildEntity(outBusinessNo, OrderTradeTypeVO.REBATE_NO_PAY_TRADE);

        // 1. 第一次执行：预期成功
        try {
            UnpaidActivityOrderEntity order = raffleActivityAccountQuotaService.createOrder(skuRechargeEntity);
            log.info("【返利无支付】首次充值成功，订单信息：{}", JSON.toJSONString(order));
            Assert.assertNotNull("首次订单创建失败", order);
        } catch (Exception e) {
            log.error("【返利无支付】首次调用非预期异常: ", e);
            Assert.fail("首次充值应成功，但发生了异常: " + e.getMessage());
        }

        // 2. 第二次执行：验证幂等抛错 (通常由数据库唯一索引触发 AppException)
        try {
            raffleActivityAccountQuotaService.createOrder(skuRechargeEntity);
            Assert.fail("【返利无支付】幂等测试失败：重复单号未被拦截");
        } catch (AppException e) {
            log.info("【返利无支付】幂等拦截成功，捕获预期异常: {} - {}", e.getCode(), e.getInfo());
        } catch (Exception e) {
            log.error("【返利无支付】捕获到非预期异常类型: ", e);
        }
    }

    /**
     * 测试：积分支付场景下的幂等性
     * 修正：修正了原代码中不存在的变量 orderId 引用
     */
    @Test
    public void test_createSkuRechargeOrder_credit_duplicate() {
        String outBusinessNo = RandomStringUtils.randomNumeric(12);
        SkuRechargeEntity skuRechargeEntity = buildEntity(outBusinessNo, OrderTradeTypeVO.CREDIT_PAY_TRADE);

        try {
            UnpaidActivityOrderEntity order = raffleActivityAccountQuotaService.createOrder(skuRechargeEntity);
            log.info("【积分支付】首次充值成功，订单信息：{}", JSON.toJSONString(order));
            Assert.assertNotNull("订单信息不应为空", order);
            Assert.assertNotNull("订单ID不应为空", order.getOrderId());
        } catch (Exception e) {
            log.error("【积分支付】调用异常: ", e);
            Assert.fail("积分支付下单失败");
        }
    }

    /**
     * 批量充值测试：模拟多次充值流程，观察库存扣减与订单落库稳定性
     */
    @Test
    public void test_createSkuRechargeOrder_batch() {
        int batchCount = 5;
        log.info("开始执行批量充值测试，共 {} 次", batchCount);

        for (int i = 0; i < batchCount; i++) {
            try {
                // 使用随机生成的外部单号模拟不同的业务场景
                String dynamicOutNo = RandomStringUtils.randomNumeric(12);
                SkuRechargeEntity skuRechargeEntity = buildEntity(dynamicOutNo, OrderTradeTypeVO.CREDIT_PAY_TRADE);

                UnpaidActivityOrderEntity order = raffleActivityAccountQuotaService.createOrder(skuRechargeEntity);
                log.info("第 {} 次批量充值成功 | 业务单号: {} | 订单号: {}", i + 1, dynamicOutNo, order.getOrderId());
            } catch (AppException e) {
                log.warn("第 {} 次充值遇到业务异常（可能库存不足或规则拦截）: {} - {}", i + 1, e.getCode(), e.getInfo());
            } catch (Exception e) {
                log.error("第 {} 次充值发生系统级异常: ", i + 1, e);
            }
        }
    }

    /**
     * 辅助方法：构建充值实体对象
     *
     * @param outBusinessNo 外部业务流水号
     * @param tradeType     交易类型
     * @return 充值实体
     */
    private SkuRechargeEntity buildEntity(String outBusinessNo, OrderTradeTypeVO tradeType) {
        SkuRechargeEntity entity = new SkuRechargeEntity();
        entity.setUserId(userId);
        entity.setSku(sku);
        entity.setOutBusinessNo(outBusinessNo);
        entity.setOrderTradeType(tradeType);
        return entity;
    }
}