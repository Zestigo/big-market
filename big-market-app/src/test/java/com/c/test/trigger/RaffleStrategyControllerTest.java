package com.c.test.trigger;

import com.alibaba.fastjson.JSON;
import com.c.api.IRaffleStrategyService;
import com.c.api.dto.RaffleAwardListRequestDTO;
import com.c.api.dto.RaffleAwardListResponseDTO;
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
        RaffleAwardListRequestDTO request = RaffleAwardListRequestDTO.builder()
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
}