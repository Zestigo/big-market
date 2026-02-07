package com.c.test.domain.award;

import com.c.domain.award.model.entity.UserAwardRecordEntity;
import com.c.domain.award.model.vo.AwardStateVO;
import com.c.domain.award.service.IAwardService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;
import java.util.Date;
import java.util.concurrent.CountDownLatch;

/**
 * 颁奖领域服务自动化测试
 * 1. 事务一致性：验证中奖记录（UserAwardRecord）与补偿任务（Task）是否在同一个数据库分片中同步落库。
 * 2. 消息驱动链路：观察发奖事件是否触发、MQ 投递是否成功以及下游状态回写。
 * 3. 分片路由正确性：配合 ShardingSphere 验证不同 userId 的数据是否准确路由到对应的物理库表。
 *
 * @author cyh
 * @date 2026/02/02
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class AwardServiceTest {

    @Resource
    private IAwardService awardService;

    /**
     * 阻塞主线程以等待异步任务回调（用于手动本地测试）
     * 使用场景：验证异步保存用户中奖记录的逻辑，防止 JVM 在回调完成前退出。
     */
    @Test
    public void test_holdForAsyncNotify() throws InterruptedException {
        // 永久阻塞，直到手动停止测试或超时
        new CountDownLatch(1).await();
    }

    /**
     * 模拟用户中奖记录产生过程（集成测试）
     * 1. 调用 awardService 接口 -> 触发本地事务（DB: record + task 入库）。
     * 2. 事务外触发 -> 异步 MQ 消息投递。
     * 3. 若投递失败 -> 依赖补偿 Job (SendMessageTaskJob) 扫描 task 表重新补发。
     */
    @Test
    public void test_saveUserAwardRecord() throws InterruptedException {
        // 模拟批量中奖场景，观察分库分表下的负载情况
        for (int i = 0; i < 10; i++) {
            UserAwardRecordEntity userAwardRecordEntity = new UserAwardRecordEntity();
            // 设置分片键（userId），确保测试数据分布均匀或固定路由
            userAwardRecordEntity.setUserId("cyh");
            userAwardRecordEntity.setActivityId(100301L);
            userAwardRecordEntity.setStrategyId(100006L);
            // 产生随机订单号，避免唯一索引冲突（DuplicateKeyException）
            userAwardRecordEntity.setOrderId(RandomStringUtils.randomNumeric(12));
            userAwardRecordEntity.setAwardId(101);
            userAwardRecordEntity.setAwardTitle("OpenAI 增加使用次数");
            userAwardRecordEntity.setAwardTime(new Date());
            userAwardRecordEntity.setAwardState(AwardStateVO.CREATE);

            try {
                awardService.saveUserAwardRecord(userAwardRecordEntity);
                log.info("测试：中奖记录保存成功 订单号:{}", userAwardRecordEntity.getOrderId());
            } catch (Exception e) {
                log.error("测试：中奖记录保存异常", e);
            }

            // 间隔发送，便于观察控制台日志与 MQ 监控看板
            Thread.sleep(500);
        }

        // 挂起主线程，防止测试进程过早退出，确保异步发送与下游消费逻辑有充足执行时间
        new CountDownLatch(1).await();
    }
}