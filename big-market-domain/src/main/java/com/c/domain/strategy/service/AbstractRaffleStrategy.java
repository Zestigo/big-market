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

/**
 * 抽奖策略抽象基类
 * 核心架构设计：
 * 1. 模板方法 (Template Method)：定义抽奖的标准算法骨架，确保子类遵循一致的执行顺序。
 * 2. 责任链模式 (Chain of Responsibility)：将“抽奖前”规则逻辑化，支持如黑名单、权重等校验。
 * 3. 决策树模式 (Decision Tree)：将“抽奖中/后”规则编排化，处理库存锁、抽奖次数锁等复杂分支逻辑。
 * 4. 关注点分离：基类负责通用流程编排与异常兜底，子类仅需实现特定的逻辑调用钩子。
 *
 * @author cyh
 * @date 2026/01/18
 */
@Slf4j
public abstract class AbstractRaffleStrategy implements IRaffleStrategy, IRaffleStock {

    /** 策略仓储：负责元数据（奖品、规则、配置）的持久化与读取 */
    protected IStrategyRepository strategyRepository;

    /** 策略调度：执行核心随机概率算法，实现从概率映射表中寻址奖品 */
    protected IStrategyDispatch strategyDispatch;

    /** 责任链工厂：根据业务配置动态构建执行链路（前置过滤） */
    protected final DefaultChainFactory defaultChainFactory;

    /** 决策树工厂：根据业务配置动态构建规则树分支（过程控制/后置校验） */
    protected final DefaultTreeFactory defaultTreeFactory;

    public AbstractRaffleStrategy(IStrategyRepository strategyRepository,
                                  IStrategyDispatch strategyDispatch,
                                  DefaultChainFactory defaultChainFactory,
                                  DefaultTreeFactory defaultTreeFactory) {
        this.strategyRepository = strategyRepository;
        this.strategyDispatch = strategyDispatch;
        this.defaultChainFactory = defaultChainFactory;
        this.defaultTreeFactory = defaultTreeFactory;
    }

    /**
     * 执行抽奖核心流程
     * * 编排生命周期：
     * 1. 准备阶段：参数合法性校验。
     * 2. 过滤阶段：执行【责任链】，处理黑名单拦截、权重预设等，产生初步的 awardId。
     * 3. 校验阶段：执行【决策树】，处理抽奖次数限制、库存预减、规则兜底等。
     * 4. 完结阶段：根据最终 awardId 聚合奖品属性，构造并返回中奖实体。
     *
     * @param raffleFactorEntity 抽奖上下文因子（用户ID、策略ID等）
     * @return 最终确定的中奖实体
     * @throws AppException 业务参数异常或核心逻辑错误
     */
    @Override
    public RaffleAwardEntity performRaffle(RaffleFactorEntity raffleFactorEntity) {
        // 1. 参数防御校验
        String userId = raffleFactorEntity.getUserId();
        Long strategyId = raffleFactorEntity.getStrategyId();
        if (null == strategyId || StringUtils.isBlank(userId)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(),
                    ResponseCode.ILLEGAL_PARAMETER.getInfo());
        }

        // 2. 责任链执行：前置规则过滤（例如：黑名单直接拦截、权重规则直接指定奖品等）
        DefaultChainFactory.StrategyAwardVO chainStrategyAwardVO = raffleLogicChain(userId, strategyId);
        Integer awardId = chainStrategyAwardVO.getAwardId();
        log.info("抽奖策略计算-责任链执行结束 userId:{} strategyId:{} awardId:{} logicModel:{}", userId, strategyId,
                awardId, chainStrategyAwardVO.getLogicModel());

        // 如果非默认抽奖产出的结果（被责任链节点“接管”直接返回），则直接构建奖励
        if (!DefaultChainFactory.LogicModel.RULE_DEFAULT.getCode()
                                                        .equals(chainStrategyAwardVO.getLogicModel())) {
            return buildRaffleAwardEntity(strategyId, awardId, null);
        }

        // 3. 决策树执行：中后置规则校验（例如：次数锁校验、库存扣减、兜底逻辑）
        DefaultTreeFactory.StrategyAwardVO treeStrategyAwardVO = raffleLogicTree(userId, strategyId, awardId);
        awardId = treeStrategyAwardVO.getAwardId();
        log.info("抽奖策略计算-决策树执行结束 userId:{} strategyId:{} awardId:{} ruleValue:{}", userId, strategyId,
                awardId, treeStrategyAwardVO.getAwardRuleValue());

        // 4. 返回聚合结果
        return buildRaffleAwardEntity(strategyId, awardId, treeStrategyAwardVO.getAwardRuleValue());
    }

    /**
     * 聚合奖品实体对象
     *
     * @param strategyId  策略ID
     * @param awardId     奖品ID
     * @param awardConfig 奖品配置（对应规则树产出的配置值，如兜底中奖说明）
     */
    private RaffleAwardEntity buildRaffleAwardEntity(Long strategyId, Integer awardId, String awardConfig) {
        StrategyAwardEntity strategyAwardEntity = strategyRepository.queryStrategyAwardEntity(strategyId,
                awardId);
        return RaffleAwardEntity.builder().awardId(awardId).awardConfig(awardConfig)
                                .awardTitle(strategyAwardEntity.getAwardTitle())
                                .sort(strategyAwardEntity.getSort()).build();
    }

    /**
     * 抽象逻辑：责任链调度逻辑钩子
     * 由子类实现具体的责任链调用逻辑，通常在此处初始化 Chain 实例并执行。
     */
    public abstract DefaultChainFactory.StrategyAwardVO raffleLogicChain(String userId, Long strategyId);

    /**
     * 抽象逻辑：决策树调度逻辑钩子
     * 由子类实现具体的树形决策逻辑，通常在此处通过 TreeFactory 加载对应的决策树。
     */
    public abstract DefaultTreeFactory.StrategyAwardVO raffleLogicTree(String userId, Long strategyId,
                                                                       Integer awardId);

}