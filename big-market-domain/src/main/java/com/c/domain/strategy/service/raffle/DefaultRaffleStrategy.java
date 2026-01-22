package com.c.domain.strategy.service.raffle;

import com.c.domain.strategy.model.vo.RuleTreeVO;
import com.c.domain.strategy.model.vo.StrategyAwardRuleModelVO;
import com.c.domain.strategy.model.vo.StrategyAwardStockKeyVO;
import com.c.domain.strategy.repository.IStrategyRepository;
import com.c.domain.strategy.service.AbstractRaffleStrategy;
import com.c.domain.strategy.service.armory.IStrategyDispatch;
import com.c.domain.strategy.service.rule.chain.ILogicChain;
import com.c.domain.strategy.service.rule.chain.factory.DefaultChainFactory;
import com.c.domain.strategy.service.rule.tree.factory.DefaultTreeFactory;
import com.c.domain.strategy.service.rule.tree.factory.engine.IDecisionTreeEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 默认抽奖策略实现类
 * * 职责：
 * 按照标准抽奖流程编排业务。继承自 {@link AbstractRaffleStrategy}。
 * * 核心设计模式应用：
 * 1. 职责链模式 (Chain)：负责抽奖【前置】过滤。处理黑名单、权重判断，最终产出“初步抽中奖品”。
 * 2. 决策树模式 (Tree)：负责抽奖【中/后置】校验。处理库存扣减、次数限制、互斥关系，产出“最终奖品”。
 * *
 */
@Slf4j
@Service
public class DefaultRaffleStrategy extends AbstractRaffleStrategy {

    public DefaultRaffleStrategy(IStrategyRepository strategyRepository, IStrategyDispatch strategyDispatch
            , DefaultChainFactory defaultChainFactory, DefaultTreeFactory defaultTreeFactory) {
        super(strategyRepository, strategyDispatch, defaultChainFactory, defaultTreeFactory);
    }

    /**
     * 执行抽奖职责链
     * 业务意图：通过链式调用，依次校验黑名单、权重值，最后执行默认的概率抽奖逻辑。
     *
     * @param userId     用户唯一标识
     * @param strategyId 策略配置ID
     * @return 职责链计算出的初步奖品结果
     */
    @Override
    public DefaultChainFactory.StrategyAwardVO raffleLogicChain(String userId, Long strategyId) {
        // 1. 通过工厂模式打开指定策略的职责链条
        ILogicChain logicChain = defaultChainFactory.openLogicChain(strategyId);
        // 2. 执行链式校验并获取结果
        return logicChain.logic(userId, strategyId);
    }

    /**
     * 执行抽奖决策树
     * 业务意图：对初步选中的奖品进行精细化准入判定（如库存、频次限制）。
     *
     * @param userId     用户ID
     * @param strategyId 策略ID
     * @param awardId    初步抽中的奖品ID
     * @return 最终决策确定的奖品（可能因规则校验失败转为发放兜底奖品）
     */
    @Override
    public DefaultTreeFactory.StrategyAwardVO raffleLogicTree(String userId, Long strategyId,
                                                              Integer awardId) {
        // 1. 查询奖品是否绑定了特定的决策规则（如该奖品是否受库存树限制）
        StrategyAwardRuleModelVO strategyAwardRuleModelVO =
                strategyRepository.queryStrategyAwardRuleModel(strategyId, awardId);

        // 如果奖品无需规则树过滤（如普通积分奖），直接构造 VO 返回
        if (null == strategyAwardRuleModelVO) {
            return DefaultTreeFactory.StrategyAwardVO.builder().awardId(awardId).build();
        }

        // 2. 根据规则模型标识（treeId），获取完整的决策树元数据视图
        RuleTreeVO ruleTreeVO =
                strategyRepository.queryRuleTreeVOByTreeId(strategyAwardRuleModelVO.getRuleModels());
        if (null == ruleTreeVO) {
            log.error("决策树配置异常，未找到对应树结构: {}", strategyAwardRuleModelVO.getRuleModels());
            throw new RuntimeException("仓储层配置错误：未找到规则树 " + strategyAwardRuleModelVO.getRuleModels());
        }

        // 3. 将决策树配置装载进决策引擎
        IDecisionTreeEngine treeEngine = defaultTreeFactory.openLogicTree(ruleTreeVO);

        // 4. 执行决策引擎处理流程，并返回最终判定结果
        return treeEngine.process(userId, strategyId, awardId);
    }

    /**
     * 【异步库存处理-消费队列】
     * 为了保证抽奖的高性能，我们在用户抽奖时只在 Redis 里预扣库存。
     * 预扣成功后，会往一个队列里丢入一条“库存待更新”的消息。
     * 本方法就是负责从这个队列里“拿”消息的任务。
     *
     * @return 待更新的库存 Key 对象
     * @throws InterruptedException 获取过程中线程中断抛出
     */
    @Override
    public StrategyAwardStockKeyVO takeQueueValue() throws InterruptedException {
        // 调用仓储层从 Redis 阻塞队列中获取库存扣减任务
        return strategyRepository.takeQueueValue();
    }

    /**
     * 异步库存处理-更新数据库
     * * 解释：
     * 这是库存处理的闭环步骤。当 takeQueueValue 拿到任务后，
     * 通过此方法将 Redis 的预扣减结果正式同步到 MySQL 数据库中。
     * 实现了“缓存抗高并发、异步任务保最终一致性”的设计。
     *
     * @param strategyId 策略ID
     * @param awardId    奖品ID
     */
    @Override
    public void updateStrategyAwardStock(Long strategyId, Integer awardId) {
        // 调用仓储层执行具体的数据库物理库存扣减操作
        strategyRepository.updateStrategyAwardStock(strategyId, awardId);
    }
}