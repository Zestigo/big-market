package com.c.test;

import com.alibaba.fastjson.JSON;
import com.c.infrastructure.dao.IAwardDao;
import com.c.infrastructure.dao.po.Award;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;
import java.util.List;

/**
 * * @Slf4j: 由 Lombok 插件提供。自动在当前类中生成一个名为 'log' 的日志对象。
 * 这样你就可以直接写 log.info("xxx")，而不需要手动写 Private static final Logger...
 * * @RunWith(SpringRunner.class): 告诉 JUnit 环境使用 Spring 的测试支持。
 * 它是 JUnit 4 的注解，作用是让测试运行在 Spring 容器环境中（这样 @Autowired 才会生效）。
 * * @SpringBootTest: 核心注解。它会寻找主配置类（@SpringBootApplication），
 * 并启动整个 Spring Boot 应用程序上下文（ApplicationContext）。
 * 有了它，你就可以在测试类里注入（@Autowired）任何你定义的 Bean。
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class AwardDaoTest {
    @Resource
    private IAwardDao awardDao;

    @Test
    public void test_queryAwardList() {
        List<Award> awards = awardDao.queryAwardList();
        log.info("测试结果："+ JSON.toJSONString(awards));
    }
}
