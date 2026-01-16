package com.c.test;

import com.c.infrastructure.redis.IRedisService;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.redisson.api.RMap;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class ApiTest {

    @Resource
    private IRedisService redisService;

    @Test
    public void test() {
        // log.info("测试完成");
        RMap<Object, Object> map = redisService.getMap("strategy_id_100001");
        map.put(1, 101);
        map.put(2, 101);
        map.put(3, 102);
        map.put(4, 105);
        map.put(5, 101);
        map.put(6, 104);
        map.put(7, 101);
        map.put(8, 108);
        map.put(9, 101);
        map.put(10, 107);
        map.put(11, 101);
        log.info("测试结果：" + redisService.getFromMap("strategy_id_100001", 1).toString());
    }
}
