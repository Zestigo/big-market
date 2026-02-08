package com.c.test.domain.rebate;

import com.alibaba.fastjson2.JSON;
import com.c.domain.rebate.model.entity.BehaviorEntity;
import com.c.domain.rebate.model.vo.BehaviorTypeVO;
import com.c.domain.rebate.service.IBehaviorRebateService;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * 行为返利服务单元测试
 *
 * @author cyh
 * @date 2026/02/05
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class BehaviorRebateServiceTest {

    /** 行为返利领域服务 */
    @Resource
    private IBehaviorRebateService behaviorRebateService;

    /**
     * 测试创建返利订单
     * 验证逻辑：输入用户行为 -> 匹配返利配置 -> 持久化流水与任务 -> 实时发送MQ
     */
    @Test
    public void test_createOrder() throws InterruptedException {
        // [步骤 1] 构造用户行为实体，模拟前端或网关传入的原始行为数据
        BehaviorEntity behavior = BehaviorEntity.builder().userId("cyh").behaviorTypeVO(BehaviorTypeVO.SIGN)
                                                // 幂等键：重复的 OutBusinessNo 会触发数据库唯一索引冲突，防止重复返利
                .outBusinessNo("20260126").build();

        // [步骤 2] 执行领域服务：创建返利订单并获取订单号集合
        List<String> orderIds = behaviorRebateService.createOrder(behavior);

        // [步骤 3] 结果打印与断言（生产环境下建议增加 Assert 断言）
        log.info("测试输入 - 用户行为实体: {}", JSON.toJSONString(behavior));
        log.info("测试输出 - 生成返利订单ID列表: {}", JSON.toJSONString(orderIds));

        new CountDownLatch(1).await();
    }

}