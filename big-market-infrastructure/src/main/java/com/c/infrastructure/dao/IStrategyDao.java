package com.c.infrastructure.dao;

import com.c.infrastructure.dao.po.Strategy;
import com.c.infrastructure.dao.po.StrategyAward;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 战略道
 *
 * @author cyh
 * @date 2026/01/15
 */
@Mapper
public interface IStrategyDao {
    List<Strategy> queryStrategyList();

}
