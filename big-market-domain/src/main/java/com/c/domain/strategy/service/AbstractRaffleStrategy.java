package com.c.domain.strategy.service;

import com.c.domain.strategy.model.entity.*;
import com.c.domain.strategy.repository.IStrategyRepository;
import com.c.domain.strategy.service.armory.IStrategyDispatch;
import com.c.domain.strategy.service.rule.chain.factory.DefaultChainFactory;
import com.c.domain.strategy.service.rule.tree.factory.DefaultTreeFactory;
import com.c.types.enums.ResponseCode;
import com.c.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.Date;

/**
 * 抽奖策略核心抽象基类 (Template Method Pattern)
 * 定义了抽奖的标准算法骨架，编排了“抽奖前、抽奖中、抽奖后”的完整生命周期。
 * * 核心架构设计：
 * 1. 责任链模式 (Chain of Responsibility)：负责【抽奖前】规则过滤，如黑名单限制、权重预设。
 * 2. 随机调度 (Strategy Dispatch)：执行核心随机概率算法，从装配好的概率映射表中抽取奖品 ID。
 * 3. 决策树模式 (Decision Tree)：负责【抽奖中/后】逻辑编排，如库存扣减、次数锁校验、兜底补偿。
 *
 * @author cyh
 * @since 2026/01/18
 */
@Slf4j
public abstract class AbstractRaffleStrategy implements IRaffleStrategy, IRaffleStock {

    /** 策略仓储：提供元数据支持（奖品配置、规则树结构、概率查找表） */
    protected IStrategyRepository strategyRepository;

    /** 策略调度：负责具体随机算法的执行，实现从概率空间到具体奖品 ID 的映射 */
    protected IStrategyDispatch strategyDispatch;

    /** 责任链工厂：根据业务规则动态构建前置过滤链路 */
    protected final DefaultChainFactory defaultChainFactory;

    /** 决策树工厂：根据业务规则编排复杂的决策分支（库存、次数锁、规则黑洞等） */
    protected final DefaultTreeFactory defaultTreeFactory;

    public AbstractRaffleStrategy(IStrategyRepository strategyRepository, IStrategyDispatch strategyDispatch,
                                  DefaultChainFactory defaultChainFactory, DefaultTreeFactory defaultTreeFactory) {
        this.strategyRepository = strategyRepository;
        this.strategyDispatch = strategyDispatch;
        this.defaultChainFactory = defaultChainFactory;
        this.defaultTreeFactory = defaultTreeFactory;
    }

    /**
     * 执行抽奖核心逻辑
     * * 编排生命周期流程：
     * 1. 准入校验：拦截非法参数（strategyId、userId）。
     * 2. 前置过滤：运行【责任链】。若命中黑名单或权重，则责任链“接管”结果并提前终止后续流程。
     * 3. 过程校验：运行【决策树】。在核心随机抽奖产生 awardId 后，进一步穿插库存预减、次数门槛校验。
     * 4. 聚合返回：根据最终确定的 awardId 封装奖励实体。
     *
     * @param raffleFactorEntity 抽奖上下文因子（包含用户标识、策略 ID、活动时效等）
     * @return {@link RaffleAwardEntity} 最终确定的中奖结果实体
     * @throws AppException 业务参数非法或规则执行发生系统性错误时抛出
     */
    @Override
    public RaffleAwardEntity performRaffle(RaffleFactorEntity raffleFactorEntity) {
        // 1. 参数防御性校验
        String userId = raffleFactorEntity.getUserId();
        Long strategyId = raffleFactorEntity.getStrategyId();
        Date endDateTime = raffleFactorEntity.getEndDateTime();

        if (null == strategyId || StringUtils.isBlank(userId)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
        }

        // 2. 【责任链阶段】执行抽奖前置过滤规则
        // 场景示例：黑名单用户直接下发阳光普照奖；满足特定权重的用户在限定池中抽奖。
        DefaultChainFactory.StrategyAwardVO chainStrategyAwardVO = raffleLogicChain(userId, strategyId);
        Integer awardId = chainStrategyAwardVO.getAwardId();
        log.info("抽奖策略执行-责任链处理结束 userId:{} strategyId:{} awardId:{} logicModel:{}",
                userId, strategyId, awardId, chainStrategyAwardVO.getLogicModel());

        // 如果责任链产出的不是“默认随机逻辑”，说明结果已被规则节点接管，直接构建奖励返回
        if (!DefaultChainFactory.LogicModel.RULE_DEFAULT.getCode().equals(chainStrategyAwardVO.getLogicModel())) {
            return buildRaffleAwardEntity(strategyId, awardId, null);
        }

        // 3. 【决策树阶段】执行中后置规则校验
        // 场景示例：核心算法抽中奖品后，校验该奖品是否有库存、用户是否有足够参与次数、是否触发兜底。
        DefaultTreeFactory.StrategyAwardVO treeStrategyAwardVO = raffleLogicTree(userId, strategyId, awardId, endDateTime);
        awardId = treeStrategyAwardVO.getAwardId();
        log.info("抽奖策略执行-决策树处理结束 userId:{} strategyId:{} awardId:{} ruleValue:{}",
                userId, strategyId, awardId, treeStrategyAwardVO.getAwardRuleValue());

        // 4. 聚合最终的中奖信息并产出
        return buildRaffleAwardEntity(strategyId, awardId, treeStrategyAwardVO.getAwardRuleValue());
    }

    /**
     * 聚合构建奖品实体对象（隔离底层存储，提供统一的领域视角）
     *
     * @param strategyId  策略 ID
     * @param awardId     奖品 ID
     * @param awardConfig 规则引擎产出的附加配置（如中奖文案、兜底描述等）
     * @return {@link RaffleAwardEntity} 完整的抽奖结果实体
     */
    private RaffleAwardEntity buildRaffleAwardEntity(Long strategyId, Integer awardId, String awardConfig) {
        StrategyAwardEntity strategyAwardEntity = strategyRepository.queryStrategyAwardEntity(strategyId, awardId);
        return RaffleAwardEntity.builder()
                                .awardId(awardId)
                                .awardConfig(awardConfig)
                                .awardTitle(strategyAwardEntity.getAwardTitle())
                                .sort(strategyAwardEntity.getSort())
                                .build();
    }

    /**
     * 抽象钩子：执行责任链调度逻辑
     * 由子类决定如何加载和触发特定的责任链。
     */
    public abstract DefaultChainFactory.StrategyAwardVO raffleLogicChain(String userId, Long strategyId);

    /**
     * 抽象钩子：执行决策树调度逻辑
     */
    public abstract DefaultTreeFactory.StrategyAwardVO raffleLogicTree(String userId, Long strategyId, Integer awardId);

    /**
     * 抽象钩子：支持活动时效校验的决策树调度逻辑
     */
    public abstract DefaultTreeFactory.StrategyAwardVO raffleLogicTree(String userId, Long strategyId,
                                                                       Integer awardId, Date endDateTime);

}