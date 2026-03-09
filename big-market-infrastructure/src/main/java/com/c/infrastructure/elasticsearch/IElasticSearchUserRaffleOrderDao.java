package com.c.infrastructure.elasticsearch;

import com.c.infrastructure.elasticsearch.po.UserRaffleOrder;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 用户抽奖订单 Elasticsearch 访问接口
 * 负责从 ES 索引中读取用户相关的抽奖订单同步数据
 *
 * @author cyh
 * @date 2026/03/09
 */
@Mapper
public interface IElasticSearchUserRaffleOrderDao {

    /**
     * 查询用户抽奖订单列表
     * @return 抽奖订单实体集合
     */
    List<UserRaffleOrder> queryUserRaffleOrderList();

}