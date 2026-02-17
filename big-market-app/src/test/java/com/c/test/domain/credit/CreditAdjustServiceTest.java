package com.c.test.domain.credit;

import com.c.domain.credit.model.entity.CreditOrderEntity;
import com.c.domain.credit.model.entity.TradeEntity;
import com.c.domain.credit.model.vo.TradeNameVO;
import com.c.domain.credit.model.vo.TradeTypeVO;
import com.c.domain.credit.service.ICreditAdjustService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

/**
 * 积分调账服务测试
 * * @author cyh
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class CreditAdjustServiceTest {

    @Resource
    private ICreditAdjustService creditAdjustService;

    /**
     * 测试正向交易：增加积分
     */
    @Test
    public void test_createOrder_forward() throws InterruptedException {
        // 1. 构造正向交易实体 (Builder 模式)
        TradeEntity tradeEntity = TradeEntity
                .builder()
                .userId("cyh")
                .tradeName(TradeNameVO.REBATE)
                .tradeType(TradeTypeVO.FORWARD)
                .tradeAmount(new BigDecimal("100.19"))
                .outBusinessNo(RandomStringUtils.randomNumeric(12)) // 动态单号防止冲突
                .build();

        // 2. 执行调账
        String orderId = creditAdjustService.createOrder(tradeEntity);

        // 3. 打印结果与断言
        log.info("正向调账完成 userId:{} orderId:{}", tradeEntity.getUserId(), orderId);
        Assert.assertNotNull(orderId);
    }

    /**
     * 测试逆向交易：扣减积分
     */
    @Test
    public void test_createOrder_reverse() throws InterruptedException {
        // 1. 构造逆向交易实体
        // 提示：扣减积分时，tradeAmount 传正数即可，内部逻辑会根据 REVERSE 进行减法操作
        TradeEntity tradeEntity = TradeEntity
                .builder()
                .userId("cyh")
                .tradeName(TradeNameVO.REBATE)
                .tradeType(TradeTypeVO.REVERSE)
                .tradeAmount(new BigDecimal("1.68"))
                .outBusinessNo("358419369938")
                .build();

        // 2. 执行调账
        String orderId = creditAdjustService.createOrder(tradeEntity);

        new CountDownLatch(1).await();
        // 3. 结果验证
        log.info("逆向调账完成 userId:{} orderId:{}", tradeEntity.getUserId(), orderId);
        Assert.assertNotNull(orderId);
    }
}