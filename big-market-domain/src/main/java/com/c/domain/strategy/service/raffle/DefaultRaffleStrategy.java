package com.c.domain.strategy.service.raffle;

import com.c.domain.strategy.model.entity.StrategyAwardEntity;
import com.c.domain.strategy.model.vo.RuleTreeVO;
import com.c.domain.strategy.model.vo.RuleWeightVO;
import com.c.domain.strategy.model.vo.StrategyAwardRuleModelVO;
import com.c.domain.strategy.model.vo.StrategyAwardStockKeyVO;
import com.c.domain.strategy.repository.IStrategyRepository;
import com.c.domain.strategy.service.AbstractRaffleStrategy;
import com.c.domain.strategy.service.IRaffleAward;
import com.c.domain.strategy.service.IRaffleRule;
import com.c.domain.strategy.service.IRaffleStock;
import com.c.domain.strategy.service.armory.IStrategyDispatch;
import com.c.domain.strategy.service.rule.chain.ILogicChain;
import com.c.domain.strategy.service.rule.chain.factory.DefaultChainFactory;
import com.c.domain.strategy.service.rule.tree.factory.DefaultTreeFactory;
import com.c.domain.strategy.service.rule.tree.factory.engine.IDecisionTreeEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 默认抽奖策略实现类
 * 职责：
 * 1. 流程落地：实现抽象基类定义的逻辑钩子，具体驱动责任链与决策树的运行。
 * 2. 领域服务聚合：作为策略领域的对外窗口，提供奖品查询、库存同步及规则判定功能。
 * 3. 异步库存一致性：配合异步工作线程，完成从 Redis 缓存预扣到数据库物理库存的最终一致性更新。
 *
 * @author cyh
 * @since 2026/01/27
 */
@Slf4j
@Service
public class DefaultRaffleStrategy extends AbstractRaffleStrategy implements IRaffleStock, IRaffleAward, IRaffleRule {

    public DefaultRaffleStrategy(IStrategyRepository strategyRepository, IStrategyDispatch strategyDispatch,
                                 DefaultChainFactory defaultChainFactory, DefaultTreeFactory defaultTreeFactory) {
        super(strategyRepository, strategyDispatch, defaultChainFactory, defaultTreeFactory);
    }

    /**
     * 运行抽奖责任链逻辑
     * 该阶段处于“抽奖前”，核心任务是判定是否存在“接管式”的中奖逻辑（如黑名单必中、权重必中）。
     *
     * @param userId     用户 ID
     * @param strategyId 策略 ID
     * @return {@link DefaultChainFactory.StrategyAwardVO} 责任链判定的初步奖品产出
     */
    @Override
    public DefaultChainFactory.StrategyAwardVO raffleLogicChain(String userId, Long strategyId) {
        // 1. 通过工厂根据策略 ID 获取该活动的执行链入口（Head 节点）
        ILogicChain logicChain = defaultChainFactory.openLogicChain(strategyId);
        // 2. 依次流转职责链节点，直到得出初步结果
        return logicChain.logic(userId, strategyId);
    }

    /**
     * 运行抽奖决策树逻辑（无活动时效校验版）
     */
    @Override
    public DefaultTreeFactory.StrategyAwardVO raffleLogicTree(String userId, Long strategyId, Integer awardId) {
        return raffleLogicTree(userId, strategyId, awardId, null);
    }

    /**
     * 运行抽奖决策树逻辑（标准版）
     * 该阶段处于“抽奖中/后”，核心任务是对抽中的奖品进行门槛校验（库存、抽奖次数、特定人群规则）。
     *
     * @param userId      用户 ID
     * @param strategyId  策略 ID
     * @param awardId     初步确定的奖品 ID
     * @param endDateTime 活动截止时间（用于节点内时效判断）
     * @return {@link DefaultTreeFactory.StrategyAwardVO} 最终裁定的中奖结果
     */
    @Override
    public DefaultTreeFactory.StrategyAwardVO raffleLogicTree(String userId, Long strategyId, Integer awardId,
                                                              Date endDateTime) {

        // 1. 检索该奖品是否配置了关联的规则模型（例如：该奖品是否挂载了库存锁或次数锁）
        StrategyAwardRuleModelVO strategyAwardRuleModelVO = strategyRepository.queryStrategyAwardRuleModel(strategyId
                , awardId);

        // 2. 快速路径：若该奖品未配置任何后置规则树，说明直接中奖，无需额外校验
        if (null == strategyAwardRuleModelVO) {
            return DefaultTreeFactory.StrategyAwardVO
                    .builder()
                    .awardId(awardId)
                    .build();
        }

        // 3. 拓扑加载：根据 RuleModels 获取完整的决策树视图（节点 + 连线）
        RuleTreeVO ruleTreeVO = strategyRepository.queryRuleTreeVOByTreeId(strategyAwardRuleModelVO.getRuleModels());
        if (null == ruleTreeVO) {
            log.error("决策引擎初始化异常，未查询到对应的规则树配置: {}", strategyAwardRuleModelVO.getRuleModels());
            throw new RuntimeException("规则树视图加载异常");
        }

        // 4. 引擎驱动：加载决策引擎并从根节点开始执行逻辑流转
        IDecisionTreeEngine treeEngine = defaultTreeFactory.openLogicTree(ruleTreeVO);
        return treeEngine.process(userId, strategyId, awardId, endDateTime);
    }

    /**
     * 延迟任务获取：从异步队列提取库存预扣流水
     * 业务背景：抽奖时先在 Redis 扣减，并将任务塞入本地/分布式队列，由此方法提取进行数据库更新。
     *
     * @return {@link StrategyAwardStockKeyVO} 包含策略 ID 与奖品 ID 的库存扣减任务
     * @throws InterruptedException 线程阻塞中断异常
     */
    @Override
    public StrategyAwardStockKeyVO takeQueueValue() throws InterruptedException {
        return strategyRepository.takeQueueValue();
    }

    /**
     * 物理库存持久化
     * 将 Redis 的扣减结果同步至数据库，确保数据的最终一致性。
     *
     * @param strategyId 策略 ID
     * @param awardId    奖品 ID
     */
    @Override
    public void updateStrategyAwardStock(Long strategyId, Integer awardId) {
        strategyRepository.updateStrategyAwardStock(strategyId, awardId);
    }

    /**
     * 查询抽奖奖品清单（原子领域服务）
     */
    @Override
    public List<StrategyAwardEntity> queryRaffleStrategyAwardList(Long strategyId) {
        return strategyRepository.queryStrategyAwardList(strategyId);
    }

    /**
     * 跨领域查询：基于活动 ID 路由奖品清单
     * 逻辑：活动领域 -> 策略 ID 映射 -> 策略领域奖品详情
     *
     * @param activityId 活动 ID
     * @return {@link List<StrategyAwardEntity>} 奖品展示信息
     */
    @Override
    public List<StrategyAwardEntity> queryRaffleStrategyAwardListByActivityId(Long activityId) {
        // 1. 获取活动关联的策略主键
        Long strategyId = strategyRepository.queryStrategyIdByActivityId(activityId);
        if (null == strategyId) {
            log.warn("查询奖品列表异常：该活动未绑定抽奖策略 activityId: {}", activityId);
            return Collections.emptyList();
        }
        // 2. 调用本类原子方法
        return queryRaffleStrategyAwardList(strategyId);
    }

    /**
     * 规则门槛查询
     * 主要用于前端展示：如“该奖品需抽奖 10 次后方可解锁”。
     *
     * @param treeIds 规则树 ID 集合（对应奖品的 rule_models）
     * @return {@link Map<String, Integer>} Key: 树 ID, Value: 解锁门槛次数
     */
    @Override
    public Map<String, Integer> queryAwardRuleLockCount(String[] treeIds) {
        return strategyRepository.queryAwardRuleLockCount(treeIds);
    }

    /**
     * 根据活动ID查询奖品权重规则配置
     *
     * @param activityId 活动ID
     * @return 权重规则列表
     */
    @Override
    public List<RuleWeightVO> queryAwardRuleWeightByActivityId(Long activityId) {
        // 1. 根据活动 ID 查询关联的策略 ID
        Long strategyId = strategyRepository.queryStrategyIdByActivityId(activityId);
        // 2. 根据策略 ID 查询对应的奖品权重规则配置
        return queryAwardRuleWeight(strategyId);
    }

    /**
     * 根据策略ID查询奖品权重规则配置
     *
     * @param strategyId 策略ID
     * @return 权重规则列表
     */
    @Override
    public List<RuleWeightVO> queryAwardRuleWeight(Long strategyId) {
        // 根据策略 ID 直接查询奖品权重规则配置（含权重阈值、奖品列表等）
        return strategyRepository.queryAwardRuleWeight(strategyId);
    }
}