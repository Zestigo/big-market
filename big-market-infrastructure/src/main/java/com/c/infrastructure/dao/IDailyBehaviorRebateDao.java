package com.c.infrastructure.dao;

import com.c.infrastructure.po.DailyBehaviorRebate;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 每日行为返利配置 DAO
 *
 * @author cyh
 * @date 2026/02/05
 */
@Mapper
public interface IDailyBehaviorRebateDao {

    /**
     * 根据行为类型查询对应的返利配置列表（通常仅查询开启状态配置）
     *
     * @param behaviorType 行为类型标识（如：sign, openai_pay）
     * @return List 每日行为返利配置持久化对象列表
     */
    List<DailyBehaviorRebate> queryDailyBehaviorRebateByBehaviorType(String behaviorType);

}