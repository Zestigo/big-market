package com.c.test.trigger;

import com.alibaba.fastjson.JSON;
import com.c.api.IRaffleActivityService;
import com.c.api.dto.ActivityDrawRequestDTO;
import com.c.api.dto.ActivityDrawResponseDTO;
import com.c.types.enums.ResponseCode;
import com.c.types.model.Response;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;

/**
 * 抽奖活动服务测试
 * 重点：验证活动装配、抽奖全链路逻辑、异常拦截处理
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class RaffleActivityControllerTest {

    @Resource
    private IRaffleActivityService raffleActivityService;

    /**
     * 活动预热/装配测试
     * 对应活动ID：100301 -> 内部应正确映射并装配策略ID：100003
     */
    @Test
    public void test_armory() {
        Long activityId = 100301L;
        Response<Boolean> response = raffleActivityService.armory(activityId);

        log.info("活动预热装配测试完成 activityId:{} 结果:{}", activityId, JSON.toJSONString(response));

        // 断言：装配必须成功
        Assert.assertEquals(ResponseCode.SUCCESS.getCode(), response.getCode());
        Assert.assertTrue(response.getData());
    }

    /**
     * 完整抽奖链路测试
     * 场景：用户 cyh 参与活动 100301
     * 预期：
     * 1. 自动扣减额度并创建订单
     * 2. 内部根据活动ID找到策略ID (100003) 进行抽奖
     * 3. 返回中奖信息或失败原因
     */
    @Test
    public void test_draw() {
        // 1. 构造请求
        ActivityDrawRequestDTO request = new ActivityDrawRequestDTO();
        request.setActivityId(100301L);
        request.setUserId("cyh");

        // 2. 发起调用
        try {
            Response<ActivityDrawResponseDTO> response = raffleActivityService.draw(request);

            // 3. 结构化日志输出（拒绝用 + 拼接，采用占位符更清晰）
            log.info("【抽奖测试】请求参数: {}", JSON.toJSONString(request));
            log.info("【抽奖测试】响应结果: {}", JSON.toJSONString(response));

            // 4. 关键业务断言
            Assert.assertNotNull("响应结果不应为空", response);

            if (ResponseCode.SUCCESS.getCode().equals(response.getCode())) {
                ActivityDrawResponseDTO data = response.getData();
                log.info("🎉 抽奖成功！奖品ID: {}, 奖品名称: {}", data.getAwardId(), data.getAwardTitle());
            } else {
                log.warn("⚠️ 抽奖业务拦截：{} ({})", response.getInfo(), response.getCode());
            }

        } catch (Exception e) {
            log.error("❌ 抽奖执行发生系统级异常", e);
            Assert.fail("不应抛出未捕获的异常（如 NPE）");
        }
    }
}