package com.c.domain.strategy.service.raffle;

import com.c.domain.strategy.model.entity.StrategyAwardEntity;
import com.c.domain.strategy.model.vo.RuleTreeVO;
import com.c.domain.strategy.model.vo.StrategyAwardRuleModelVO;
import com.c.domain.strategy.model.vo.StrategyAwardStockKeyVO;
import com.c.domain.strategy.repository.IStrategyRepository;
import com.c.domain.strategy.service.AbstractRaffleStrategy;
import com.c.domain.strategy.service.IRaffleAward;
import com.c.domain.strategy.service.IRaffleStock;
import com.c.domain.strategy.service.armory.IStrategyDispatch;
import com.c.domain.strategy.service.rule.chain.ILogicChain;
import com.c.domain.strategy.service.rule.chain.factory.DefaultChainFactory;
import com.c.domain.strategy.service.rule.tree.factory.DefaultTreeFactory;
import com.c.domain.strategy.service.rule.tree.factory.engine.IDecisionTreeEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 默认抽奖策略实现类 (Default Raffle Strategy Implementation)
 * 1. 流程编排：继承 {@link AbstractRaffleStrategy} 模板基类，实现“初步过滤 -> 随机抽奖 -> 准入决策”的标准抽奖流水线。
 * 2. 模式应用：
 * - 职责链模式 (Chain)：解决“抽奖前”规则过滤，如黑名单抢占、权重配置切换、默认概率抽奖。
 * - 决策树模式 (Tree)：解决“抽奖后”复杂准入判定，如库存实时扣减、中奖次数限制、兜底方案执行。
 * 3. 异步一致性：实现 {@link IRaffleStock} 接口，充当 Redis 预扣库存向 MySQL 持久化转移的调度中心。
 *
 * @author cyh
 * @date 2026/01/27
 */
@Slf4j
@Service
public class DefaultRaffleStrategy extends AbstractRaffleStrategy implements IRaffleStock, IRaffleAward {

    /**
     * 构造函数注入说明：
     *
     * @param strategyRepository  策略仓储：负责领域实体与持久化层的数据交互。
     * @param strategyDispatch    策略调度：提供概率装配及随机数寻址的核心能力。
     * @param defaultChainFactory 职责链工厂：根据策略配置，动态构建并开启对应的校验链条。
     * @param defaultTreeFactory  决策树工厂：负责加载决策树模型并实例化决策执行引擎。
     */
    public DefaultRaffleStrategy(IStrategyRepository strategyRepository, IStrategyDispatch strategyDispatch
            , DefaultChainFactory defaultChainFactory, DefaultTreeFactory defaultTreeFactory) {
        super(strategyRepository, strategyDispatch, defaultChainFactory, defaultTreeFactory);
    }

    /**
     * 执行抽奖职责链（核心抽奖动作）
     * 这是抽奖的第一阶段。利用职责链模式将“黑名单规则”、“权重规则”、“默认抽奖逻辑”解耦。
     * 链条中的每一个节点都有权直接返回结果（如黑名单直接命中），或者透传给下一个节点。
     *
     * @param userId     参与抽奖的用户 ID
     * @param strategyId 活动绑定的策略 ID
     * @return {@link DefaultChainFactory.StrategyAwardVO} 包含初步抽中的奖品 ID 及规则配置说明
     */
    @Override
    public DefaultChainFactory.StrategyAwardVO raffleLogicChain(String userId, Long strategyId) {
        // 1. 获取指定策略的职责链头部节点。该链条通常在系统启动或策略装配时预生成。
        ILogicChain logicChain = defaultChainFactory.openLogicChain(strategyId);

        // 2. 沿着职责链执行逻辑判断。若链条配置为 [Blacklist -> Weight -> Default]，则依次匹配，直至产生初步结果。
        return logicChain.logic(userId, strategyId);
    }

    /**
     * 执行抽奖决策树（精细化准入校验）
     * 这是抽奖的第二阶段。针对第一阶段产生的“初步奖品”，进一步校验其领取合法性。
     * 例如：校验该奖品的 Redis 实时库存是否充足、该用户领奖次数是否超限等。
     *
     * @param userId     参与抽奖的用户 ID
     * @param strategyId 活动绑定的策略 ID
     * @param awardId    第一阶段职责链产出的初步奖品 ID
     * @return {@link DefaultTreeFactory.StrategyAwardVO} 经过最终决策判定的奖品结果（含兜底处理）
     */
    @Override
    public DefaultTreeFactory.StrategyAwardVO raffleLogicTree(String userId, Long strategyId,
                                                              Integer awardId) {
        // 1. 查找奖品元数据：确定该奖品是否挂载了后续决策树规则（如：库存规则树、次数限制规则树）
        StrategyAwardRuleModelVO strategyAwardRuleModelVO =
                strategyRepository.queryStrategyAwardRuleModel(strategyId, awardId);

        // 路径分支：若奖品无规则绑定（如普通积分奖），直接封装结果并完成抽奖流程
        if (null == strategyAwardRuleModelVO) {
            return DefaultTreeFactory.StrategyAwardVO.builder().awardId(awardId).build();
        }

        // 2. 加载决策树结构：获取包含根节点、决策节点、决策连线逻辑的 VO 视图
        RuleTreeVO ruleTreeVO =
                strategyRepository.queryRuleTreeVOByTreeId(strategyAwardRuleModelVO.getRuleModels());
        if (null == ruleTreeVO) {
            log.error("决策引擎初始化失败：未找到对应的树结构配置 ID: {}", strategyAwardRuleModelVO.getRuleModels());
            throw new RuntimeException("规则树视图加载异常，请检查仓储配置: " + strategyAwardRuleModelVO.getRuleModels());
        }

        // 3. 初始化决策引擎：通过工厂模式装载规则树上下文，准备执行递归判定
        IDecisionTreeEngine treeEngine = defaultTreeFactory.openLogicTree(ruleTreeVO);

        // 4. 执行引擎决策：从根节点开始，根据节点规则执行结果沿连线跳转，最终返回判定后的奖品 ID 及状态
        return treeEngine.process(userId, strategyId, awardId);
    }

    /**
     * 异步库存任务提取
     * 在高并发场景下，为了保护数据库，库存扣减动作在 Redis 内存中异步完成。
     * 此时需要后台 Worker 不断从阻塞队列中拉取扣减流水，准备同步回数据库。
     *
     * @return {@link StrategyAwardStockKeyVO} 封装了待同步的策略 ID 与奖品 ID 的库存流水信息
     * @throws InterruptedException 当线程在阻塞等待消息时被中断抛出
     */
    @Override
    public StrategyAwardStockKeyVO takeQueueValue() throws InterruptedException {
        // 直接从仓储层持有的分布式阻塞队列中获取最新的一条库存变更记录
        return strategyRepository.takeQueueValue();
    }

    /**
     * 异步库存持久化同步
     * 这是典型的“缓存抗并发，DB 最终一致性”设计模式。
     * 此方法将缓存层已经扣减成功的确定性结果，通过物理 SQL 执行，同步到数据库的 strategy_award 表。
     *
     * @param strategyId 策略配置 ID
     * @param awardId    奖品配置 ID
     */
    @Override
    public void updateStrategyAwardStock(Long strategyId, Integer awardId) {
        // 调用仓储执行：UPDATE strategy_award SET surplus = surplus - 1 WHERE ...
        strategyRepository.updateStrategyAwardStock(strategyId, awardId);
    }

    /**
     * 查询抽奖策略可用奖品清单
     *
     * @param strategyId 策略配置 ID
     * @return 包含奖品基础信息、权重、原始库存量的实体列表
     */
    @Override
    public List<StrategyAwardEntity> queryRaffleStrategyAwardList(Long strategyId) {
        // 透传调用仓储层，获取用于前端展示或规则预加载的奖品数据集合
        return strategyRepository.queryStrategyAwardList(strategyId);
    }
}