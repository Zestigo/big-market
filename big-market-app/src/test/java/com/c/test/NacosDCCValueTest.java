package com.c.test;

import com.alibaba.nacos.api.config.ConfigService;
import com.c.config.NacosClientConfigProperties;
import com.c.trigger.http.RaffleActivityController;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;
import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;

/**
 * DCC 动态配置功能集成测试
 * 匹配配置：@DCCConfiguration(prefix = "raffle.activity", dataId = "raffle-config.yaml")
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class NacosDCCValueTest {

    @Resource
    private NacosClientConfigProperties nacosClientConfigProperties;

    @Resource
    private RaffleActivityController raffleActivityController;

    @Resource
    private ConfigService configService;

    @Test
    public void test_nacos_config_and_refresh() throws Exception {
        // 1. 配置校验
        log.info(">>>>> [Step 1] YAML 配置校验: Namespace={}", nacosClientConfigProperties.getNamespace());

        // 2. 初始状态获取
        Field field = raffleActivityController
                .getClass()
                .getDeclaredField("degradeSwitch");
        field.setAccessible(true);
        String beforeValue = (String) field.get(raffleActivityController);
        log.info(">>>>> [Step 2] 初始内存值: {}", beforeValue);

        // 3. 模拟远程推送
        // 必须对应 @DCCConfiguration 中的 dataId
        String targetDataId = "raffle-config.yaml";
        String targetGroup = "DEFAULT_GROUP";

        // 必须对应 prefix = "raffle.activity" 的层级结构
        // 我们想把值改成 close
        String newValueForVerify = "close";
        String yamlContent = "raffle:\n" + "  activity:\n" + "    degradeSwitch: " + newValueForVerify;

        log.info(">>>>> [Step 3] 模拟推送 YAML 内容到 DataID: {}\n{}", targetDataId, yamlContent);
        boolean isPublishOk = configService.publishConfig(targetDataId, targetGroup, yamlContent);

        if (!isPublishOk) {
            log.error("❌ Nacos 发布失败！");
            return;
        }

        // 4. 等待回调
        log.info(">>>>> [Step 4] 等待监听器回调...");
        TimeUnit.SECONDS.sleep(2);

        // 5. 最终验证
        String afterValue = (String) field.get(raffleActivityController);
        log.info(">>>>> [Step 5] 最终内存值: {}", afterValue);

        // 注意：如果你的 BeanFactory 还没加 YAML 解析功能，这里拿到的可能是整个 YAML 字符串
        if (newValueForVerify.equals(afterValue)) {
            log.info("✅ [Success] 动态配置链路全线贯通！");
        } else {
            log.warn("⚠️ [Fail] 内存值未更新或匹配失败。当前内存值内容：\n{}", afterValue);
            log.info("💡 cyh 提示：如果输出是整段 YAML 文本，说明 BeanFactory 需要升级 YAML 解析器了。");
        }
    }
}