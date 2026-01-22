package com.c.infrastructure.dao;

import com.c.infrastructure.po.StrategyAward;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 策略奖品配置查询 DAO
 * 职责：
 * 负责访问 `strategy_award` 表，管理抽奖策略中奖品的概率、库存、以及关联的规则模型映射。
 *
 * @author cyh
 * @date 2026/01/20
 */
@Mapper
public interface IStrategyAwardDao {

    /**
     * 查询所有策略奖品（通常用于全量预热或管理端导出）
     *
     * @return 奖品持久化对象列表
     */
    List<StrategyAward> queryStrategyAwardList();

    /**
     * 根据策略ID查询该策略关联的所有奖品信息
     * 业务用途：用于策略装配阶段，将奖品 ID 和概率加载到内存/Redis 中。
     *
     * @param strategyId 策略ID
     * @return 奖品配置列表（包含奖品ID、名称、概率、总库存、剩余库存等）
     */
    List<StrategyAward> queryStrategyAwardListByStrategyId(Long strategyId);

    /**
     * 查询奖品关联的规则模型标识（如：该奖品是否绑定了特定的决策树）
     * 业务逻辑：用于判断抽中此奖品后，是否需要进一步执行规则过滤。
     *
     * @param strategyAward 包含 strategyId 和 awardId 的入参对象
     * @return 规则模型标识字符串（例如：rule_tree_id）
     */
    String queryStrategyAwardRuleModel(StrategyAward strategyAward);

    /**
     * 更新奖品物理库存
     * 业务用途：在异步消费库存任务时，将 Redis 预扣减的结果同步到 MySQL。
     * 算法实现：建议在 SQL 中使用 `update ... set surplus = surplus - 1 where ... and surplus > 0` 保证原子性。
     *
     * @param strategyAward 包含 strategyId 和 awardId 的更新对象
     */
    void updateStrategyAwardStock(StrategyAward strategyAward);

}