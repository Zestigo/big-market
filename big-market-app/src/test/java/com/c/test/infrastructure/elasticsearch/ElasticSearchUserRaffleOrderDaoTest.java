package com.c.test.infrastructure.elasticsearch;

import com.alibaba.fastjson.JSON;
import com.c.infrastructure.elasticsearch.IElasticSearchUserRaffleOrderDao;
import com.c.infrastructure.elasticsearch.po.UserRaffleOrder;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;
import java.util.List;

/**
 * 用户抽奖订单 Elasticsearch DAO 单元测试
 *
 * @author cyh
 * @date 2026/03/09
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class ElasticSearchUserRaffleOrderDaoTest {

    @Resource
    private IElasticSearchUserRaffleOrderDao elasticSearchUserRaffleOrderDao;

    /**
     * 测试查询用户抽奖订单列表
     * 验证从 Elasticsearch 中通过 JDBC/SQL 方式获取数据的正确性
     */
    @Test
    public void test_queryUserRaffleOrderList() {
        List<UserRaffleOrder> userRaffleOrders = elasticSearchUserRaffleOrderDao.queryUserRaffleOrderList();
        log.info("测试结果：{}", JSON.toJSONString(userRaffleOrders));
    }

}