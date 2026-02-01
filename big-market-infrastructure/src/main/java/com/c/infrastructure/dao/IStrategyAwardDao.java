package com.c.infrastructure.dao;

import com.c.infrastructure.po.StrategyAward;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 策略奖品配置持久化访问对象 (DAO)
 * 职责：
 * 负责 `strategy_award` 表的数据交互。承载抽奖策略中奖品的基础配置、概率权重、
 * 物理库存状态以及扩展规则模型（如决策树 ID）的持久化存储。
 *
 * @author cyh
 * @date 2026/01/20
 */
@Mapper
public interface IStrategyAwardDao {

    /**
     * 查询全量策略奖品配置
     * 场景：通常用于系统启动时的全量缓存预热或后台管理系统的配置导出。
     *
     * @return 奖品持久化对象 (PO) 列表
     */
    List<StrategyAward> queryStrategyAwardList();

    /**
     * 根据策略 ID 检索关联奖品集合
     * 业务用途：用于策略装配阶段。通过此接口获取奖品概率分布，以构建内存算法引擎或初始化 Redis 抽奖池。
     *
     * @param strategyId 策略唯一标识
     * @return 奖品配置详情列表（含 ID、权重概率、库存水位线等）
     */
    List<StrategyAward> queryStrategyAwardListByStrategyId(Long strategyId);

    /**
     * 获取奖品绑定的规则模型标识
     * 业务逻辑：判定用户抽中该奖品后，是否触发后续的“奖品前置规则”或“抽奖决策树”（如：黑名单校验、积分抵扣规则）。
     *
     * @param strategyAward 包含 strategyId 与 awardId 的检索条件对象
     * @return 规则模型标识（如：rule_models 或 tree_id 字符串）
     */
    String queryStrategyAwardRuleModel(StrategyAward strategyAward);

    /**
     * 同步奖品物理库存（异步扣减）
     * 核心逻辑：在 Job 消费预扣流水时调用。将 Redis 的预减状态持久化至 MySQL 磁盘。
     * 约束说明：SQL 实现必须包含 `surplus_count > 0` 的原子检查条件，防止在高并发极端场景下出现超卖负数。
     *
     * @param strategyAward 包含策略 ID 与奖品 ID 的更新载体
     */
    void updateStrategyAwardStock(StrategyAward strategyAward);

    /**
     * 查询单个奖品配置详情
     * 业务场景：在计算奖品权重或核销奖品发放资格时，获取该奖品的最新的配置镜像。
     *
     * @param strategyAward 包含策略 ID 与奖品 ID 的检索对象
     * @return 策略奖品持久化实体
     */
    StrategyAward queryStrategyAwardEntity(StrategyAward strategyAward);
}