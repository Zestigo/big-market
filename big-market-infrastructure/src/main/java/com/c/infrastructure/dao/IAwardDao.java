package com.c.infrastructure.dao;

import com.c.infrastructure.dao.po.Award;
import com.c.infrastructure.dao.po.StrategyRule;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface IAwardDao {
    List<Award> queryAwardList();
}
