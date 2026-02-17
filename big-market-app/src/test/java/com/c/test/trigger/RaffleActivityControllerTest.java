package com.c.test.trigger;

import com.alibaba.fastjson.JSON;
import com.c.api.IRaffleActivityService;
import com.c.api.dto.*;
import com.c.types.enums.ResponseCode;
import com.c.types.model.Response;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * 抽奖活动 Trigger 层集成测试
 *
 * @author cyh
 * @date 2026/02/16
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class RaffleActivityControllerTest {

    @Resource
    private IRaffleActivityService raffleActivityService;

    /**
     * 活动策略预热装配测试
     */
    @Test
    public void test_armory() {
        Long activityId = 100301L;
        Response<Boolean> response = raffleActivityService.armory(activityId);

        log.info("活动装配测试 activityId:{} 结果:{}", activityId, JSON.toJSONString(response));
        Assert.assertTrue("活动装配应返回 true", response.getData());
    }

    /**
     * 完整抽奖流程测试
     */
    @Test
    public void test_draw() {
        ActivityDrawRequestDTO request = new ActivityDrawRequestDTO();
        request.setActivityId(100301L);
        request.setUserId("cyh");

        Response<ActivityDrawResponseDTO> response = raffleActivityService.draw(request);
        log.info("抽奖测试完成 请求:{} 响应:{}", JSON.toJSONString(request), JSON.toJSONString(response));

        Assert.assertNotNull("响应对象不应为空", response);
    }

    /**
     * 黑名单用户拦截测试
     */
    @Test
    public void test_blacklist_draw() {
        ActivityDrawRequestDTO request = new ActivityDrawRequestDTO();
        request.setActivityId(100301L);
        request.setUserId("user001");

        Response<ActivityDrawResponseDTO> response = raffleActivityService.draw(request);
        log.info("黑名单抽奖测试 请求:{} 响应:{}", JSON.toJSONString(request), JSON.toJSONString(response));

        Assert.assertNotNull(response.getData());
    }

    /**
     * 日历签到返利测试
     */
    @Test
    public void test_calendarSignRebate() {
        String userId = "user001";
        Response<Boolean> response = raffleActivityService.calendarSignRebate(userId);

        log.info("日历签到返利测试 userId:{} 结果:{}", userId, JSON.toJSONString(response));
        Assert.assertTrue("签到返利应执行成功", response.getData());
    }

    /**
     * 查询用户活动账户额度测试
     */
    @Test
    public void test_queryUserActivityAccount() {
        UserActivityAccountRequestDTO request = new UserActivityAccountRequestDTO();
        request.setActivityId(100301L);
        request.setUserId("cyh");

        Response<UserActivityAccountResponseDTO> response = raffleActivityService.queryUserActivityAccount(request);
        log.info("账户额度查询测试 结果:{}", JSON.toJSONString(response));

        Assert.assertEquals(ResponseCode.SUCCESS.getCode(), response.getCode());
    }

    /**
     * 查询活动关联的 SKU 商品列表测试
     */
    @Test
    public void test_querySkuProductListByActivityId() {
        Long activityId = 100301L;
        Response<List<SkuProductResponseDTO>> response =
                raffleActivityService.querySkuProductListByActivityId(activityId);

        log.info("查询 SKU 商品列表测试 activityId:{} 结果:{}", activityId, JSON.toJSONString(response));
        Assert.assertNotNull(response.getData());
    }

    /**
     * 查询用户积分账户余额测试
     */
    @Test
    public void test_queryUserCreditAccount() {
        String userId = "cyh";
        Response<BigDecimal> response = raffleActivityService.queryUserCreditAccount(userId);

        log.info("查询用户积分余额测试 userId:{} 结果:{}", userId, JSON.toJSONString(response));
        Assert.assertNotNull(response.getData());
    }

    /**
     * 积分兑换商品全链路测试
     */
    @Test
    public void test_creditPayExchangeSku() throws InterruptedException {
        SkuProductShopCartRequestDTO request = new SkuProductShopCartRequestDTO();
        request.setUserId("cyh");
        request.setSku(9011L);

        Response<Boolean> response = raffleActivityService.creditPayExchangeSku(request);
        log.info("积分兑换商品测试 请求:{} 结果:{}", JSON.toJSONString(request), JSON.toJSONString(response));

        // 阻塞主线程，观察异步消息任务持久化或 MQ 发送情况
        new CountDownLatch(1).await();
    }
}