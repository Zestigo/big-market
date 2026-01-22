package com.c.infrastructure.dao;

import com.c.infrastructure.po.Strategy;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 抽奖策略配置查询 DAO
 * 职责：
 * 负责访问 `strategy` 主表，获取策略的全局配置。
 * 包含策略的职责链规则（Rule Models）以及权重配置（Rule Weight）等核心元数据。
 *
 *
 *
 * @author cyh
 * @date 2026/01/21
 */
@Mapper
public interface IStrategyDao {

    /**
     * 查询全量策略列表
     * 业务用途：通常用于系统启动时的策略预热、管理后台列表展示或定时任务调度。
     *
     * @return 策略持久化对象列表
     */
    List<Strategy> queryStrategyList();

    /**
     * 根据策略ID查询具体的策略配置详情
     * 业务逻辑：
     * 用于加载该策略下挂载的所有逻辑规则模型（如：rule_models = "rule_blacklist,rule_weight"）。
     * 仓储层会基于此方法的返回值，在内存中组装职责链。
     *
     * @param strategyId 策略唯一标识
     * @return 策略持久化对象（包含 strategy_id, strategy_desc, rule_models 等字段）
     */
    Strategy queryStrategyEntityByStrategyId(Long strategyId);

}