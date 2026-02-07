package com.c.test.trigger;

import com.alibaba.fastjson.JSON;
import com.c.api.IRaffleStrategyService;
import com.c.api.dto.RaffleAwardListRequestDTO;
import com.c.api.dto.RaffleAwardListResponseDTO;
import com.c.api.dto.RaffleStrategyRuleWeightRequestDTO;
import com.c.api.dto.RaffleStrategyRuleWeightResponseDTO;
import com.c.types.enums.ResponseCode; // 假设你有枚举定义状态码
import com.c.types.model.Response;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;
import java.util.List;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class RaffleStrategyControllerTest {

    @Resource
    private IRaffleStrategyService raffleStrategyService;

    @Test
    public void test_queryRaffleAwardList_success() {
        // 1. 构建请求参数（更清晰的对象初始化）
        RaffleAwardListRequestDTO request = RaffleAwardListRequestDTO
                .builder()
                .userId("cyh")
                .activityId(100301L)
                .build();

        // 2. 执行调用
        log.info("查询抽奖奖品列表开始，请求参数：{}", JSON.toJSONString(request));
        Response<List<RaffleAwardListResponseDTO>> response = raffleStrategyService.queryRaffleAwardList(request);

        // 3. 核心断言
        Assert.assertNotNull("响应结果不应为空", response);
        // 假设成功状态码为 "0000"
        Assert.assertEquals("接口响应状态码不匹配", "0000", response.getCode());
        Assert.assertNotNull("奖品列表不应为空", response.getData());

        // 4. 打印格式化后的结果（便于排查问题）
        log.info("测试结果：{}", JSON.toJSONString(response, true));
    }

    @Test
    public void test_queryRaffleAwardList_invalidParams() {
        // 异常场景测试：测试无效 ActivityId
        RaffleAwardListRequestDTO request = new RaffleAwardListRequestDTO();
        request.setUserId("cyh");
        request.setActivityId(null);

        Response<List<RaffleAwardListResponseDTO>> response = raffleStrategyService.queryRaffleAwardList(request);

        // 验证系统是否优雅处理了非法参数
        Assert.assertNotEquals("0000", response.getCode());
    }

    @Test
    public void test_queryRaffleStrategyRuleWeight() {
        // 1. 构建请求对象：模拟用户查询当前活动下的抽奖权重规则进度
        RaffleStrategyRuleWeightRequestDTO request = new RaffleStrategyRuleWeightRequestDTO();
        request.setUserId("cyh");
        request.setActivityId(100301L);

        // 2. 调用接口：获取权重规则配置、关联奖品以及用户当前的累计参与次数
        Response<List<RaffleStrategyRuleWeightResponseDTO>> response =
                raffleStrategyService.queryRaffleStrategyRuleWeight(request);

        // 3. 记录日志：打印请求报文与响应报文，便于在测试报告中追溯业务逻辑
        log.info("请求参数：{}", JSON.toJSONString(request));
        log.info("测试结果：{}", JSON.toJSONString(response));

        // 4. 严谨性断言：确保接口响应成功且数据结构符合预期
        Assert.assertEquals(ResponseCode.SUCCESS.getCode(), response.getCode());
        Assert.assertNotNull(response.getData());

        // 5. 业务逻辑深度校验（可选）：验证返回的权重档位是否有序，或奖品列表是否完整
        if (!response
                .getData()
                .isEmpty()) {
            RaffleStrategyRuleWeightResponseDTO firstRule = response
                    .getData()
                    .get(0);
            // 校验返回的进度值是否正确透传（非负数）
            Assert.assertTrue("用户累计抽奖次数应大于等于0", firstRule.getUserActivityTotalCount() >= 0);
            // 校验奖品集合是否已装配
            Assert.assertFalse("权重规则下的奖品列表不应为空", firstRule
                    .getStrategyAwards()
                    .isEmpty());
        }
    }

}