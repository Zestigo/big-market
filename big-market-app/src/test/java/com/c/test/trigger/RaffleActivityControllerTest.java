package com.c.test.trigger;

import com.alibaba.fastjson.JSON;
import com.c.api.IRaffleActivityService;
import com.c.api.dto.ActivityDrawRequestDTO;
import com.c.api.dto.ActivityDrawResponseDTO;
import com.c.api.dto.UserActivityAccountRequestDTO;
import com.c.api.dto.UserActivityAccountResponseDTO;
import com.c.types.enums.ResponseCode;
import com.c.types.model.Response;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;
import java.util.concurrent.CountDownLatch;

/**
 * 抽奖活动 Trigger 层集成测试
 * 覆盖：活动装配、全链路抽奖、返利资格查询、账户额度查询
 *
 * @author cyh
 * @date 2026/02/07
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

        Assert.assertEquals(ResponseCode.SUCCESS.getCode(), response.getCode());
        Assert.assertTrue("活动装配应返回 true", response.getData());
    }

    /**
     * 完整抽奖流程测试（包含扣减额度、执行策略、生成结果）
     */
    @Test
    public void test_draw() {
        ActivityDrawRequestDTO request = new ActivityDrawRequestDTO();
        request.setActivityId(100301L);
        request.setUserId("cyh");

        Response<ActivityDrawResponseDTO> response = raffleActivityService.draw(request);
        log.info("抽奖测试完成 请求:{} 响应:{}", JSON.toJSONString(request), JSON.toJSONString(response));

        Assert.assertNotNull("响应对象不应为空", response);

        if (ResponseCode.SUCCESS
                .getCode()
                .equals(response.getCode())) {
            ActivityDrawResponseDTO data = response.getData();
            log.info("🎉 抽奖成功：奖品ID={}, 标题={}", data.getAwardId(), data.getAwardTitle());
            Assert.assertNotNull("中奖后奖品ID不能为空", data.getAwardId());
        } else {
            log.warn("抽奖被拦截：{} - {}", response.getCode(), response.getInfo());
        }
    }

    /**
     * 黑名单用户抽奖测试
     * 场景：验证当用户 user001 命中黑名单规则时，责任链是否正确拦截并返回兜底奖品。
     */
    @Test
    public void test_blacklist_draw() throws InterruptedException {
        ActivityDrawRequestDTO request = new ActivityDrawRequestDTO();
        request.setActivityId(100301L);
        request.setUserId("user001");

        Response<ActivityDrawResponseDTO> response = raffleActivityService.draw(request);

        log.info("黑名单抽奖测试 请求:{} 响应:{}", JSON.toJSONString(request), JSON.toJSONString(response));

        new CountDownLatch(1).await();

        // 断言：黑名单拦截通常应返回成功码，但奖品 ID 应为策略中配置的黑名单兜底奖品
        Assert.assertNotNull(response.getData());
    }

    /**
     * 日历签到返利测试
     * 场景：用户完成签到动作后，触发返利流程，通常涉及账户额度增加。
     */
    @Test
    public void test_calendarSignRebate() {
        String userId = "user001";
        Response<Boolean> response = raffleActivityService.calendarSignRebate(userId);

        log.info("日历签到返利测试 userId:{} 结果:{}", userId, JSON.toJSONString(response));

        // 断言：验证接口调用成功且业务逻辑处理完成
        Assert.assertTrue("签到返利应执行成功", response.getData());
    }

    /**
     * 日历签到返利资格校验测试
     */
    @Test
    public void test_isCalendarSignRebate() {
        String userId = "cyh";
        Response<Boolean> response = raffleActivityService.isCalendarSignRebate(userId);

        log.info("签到返利资格查询 userId:{} 结果:{}", userId, JSON.toJSONString(response));

        Assert.assertEquals(ResponseCode.SUCCESS.getCode(), response.getCode());
        Assert.assertNotNull("业务结果不应为空", response.getData());
    }

    /**
     * 查询用户活动账户额度测试（总/日/月额度）
     */
    @Test
    public void test_queryUserActivityAccount() {
        UserActivityAccountRequestDTO request = new UserActivityAccountRequestDTO();
        request.setActivityId(100301L);
        request.setUserId("cyh");

        Response<UserActivityAccountResponseDTO> response = raffleActivityService.queryUserActivityAccount(request);
        log.info("账户额度查询测试 结果:{}", JSON.toJSONString(response));

        Assert.assertEquals(ResponseCode.SUCCESS.getCode(), response.getCode());
        if (null != response.getData()) {
            UserActivityAccountResponseDTO data = response.getData();
            log.info("账户镜像：总额度={}, 日剩余={}, 月剩余={}", data.getTotalCount(), data.getDayCountSurplus(),
                    data.getMonthCountSurplus());
        }
    }

}