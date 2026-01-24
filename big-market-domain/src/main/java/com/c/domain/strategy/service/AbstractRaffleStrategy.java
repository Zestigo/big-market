package com.c.domain.strategy.service;

import com.c.domain.strategy.model.entity.*;
import com.c.domain.strategy.model.vo.RuleLogicCheckTypeVO;
import com.c.domain.strategy.model.vo.RuleTreeNodeVO;
import com.c.domain.strategy.model.vo.StrategyAwardRuleModelVO;
import com.c.domain.strategy.repository.IStrategyRepository;
import com.c.domain.strategy.service.armory.IStrategyDispatch;
import com.c.domain.strategy.service.rule.chain.ILogicChain;
import com.c.domain.strategy.service.rule.chain.factory.DefaultChainFactory;
import com.c.domain.strategy.service.rule.tree.ILogicTreeNode;
import com.c.domain.strategy.service.rule.tree.factory.DefaultTreeFactory;
import com.c.types.enums.ResponseCode;
import com.c.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * @author cyh
 * @description 抽奖策略抽象基类
 * @details 核心架构：采用【模板方法模式】定义抽奖的标准算法骨架。
 * 1. 流程编排：将通用的“参数校验”、“责任链初始化”、“抽奖中/后规则触发”定义在基类中。
 * 2. 解耦设计：通过【责任链模式】解耦前置规则（如黑名单、权重），通过【工厂模式】动态构建执行链路。
 * 3. 扩展性：子类只需关注具体的逻辑钩子实现，确保了核心抽奖主流程的稳定与封闭。
 * @date 2026/01/18
 */
@Slf4j
public abstract class AbstractRaffleStrategy implements IRaffleStrategy, IRaffleStock {

    /** 策略仓储服务：负责策略配置、奖品元数据、规则模型等数据的查询与持久化 */
    protected IStrategyRepository strategyRepository;

    /** 策略调度服务：执行概率算法的核心入口，负责从装配好的奖品池中随机获取奖品 ID */
    protected IStrategyDispatch strategyDispatch;

    /** 责任链工厂：用于根据策略配置动态组装逻辑校验链条（如：黑名单节点 -> 权重节点 -> 默认节点） */
    protected final DefaultChainFactory defaultChainFactory;
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
     * 执行抽奖决策主流程
     * 编排步骤：
     * 1. 参数校验：确保用户 ID 与策略 ID 合法。
     * 2. 责任链执行：流转“抽奖前”过滤规则，获取初步抽奖结果（可能是规则接管返回，也可能是默认随机产出）。
     * 3. 抽奖中规则校验：基于初步奖品 ID，校验该奖品是否存在互斥、库存锁等“抽奖中”规则。
     * 4. 结果组装：封装最终的奖品实体并返回。
     *
     * @param raffleFactorEntity 抽奖因子实体：包含 userId, strategyId 等上下文信息
     * @return RaffleAwardEntity 最终抽奖结果实体
     */
    @Override
    public RaffleAwardEntity performRaffle(RaffleFactorEntity raffleFactorEntity) {
        // 1. 基础参数校验（防御性编程）
        String userId = raffleFactorEntity.getUserId();
        Long strategyId = raffleFactorEntity.getStrategyId();
        if (null == strategyId || StringUtils.isBlank(userId)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(),
                    ResponseCode.ILLEGAL_PARAMETER.getInfo());
        }
        DefaultChainFactory.StrategyAwardVO chainStrategyAwardVO = raffleLogicChain(userId, strategyId);
        Integer awardId = chainStrategyAwardVO.getAwardId();
        log.info("抽奖策略计算-责任链 {} {} {} {}", userId, strategyId, awardId, chainStrategyAwardVO.getLogicModel());
        if (!chainStrategyAwardVO.getLogicModel()
                                 .equals(DefaultChainFactory.LogicModel.RULE_DEFAULT.getCode())) {
            // TODO
            return buildRaffleAwardEntity(strategyId, awardId, null);
        }

        DefaultTreeFactory.StrategyAwardVO treeStrategyAwardVO = raffleLogicTree(userId, strategyId, awardId);
        awardId = treeStrategyAwardVO.getAwardId();
        log.info("抽奖策略计算-规则树 {} {} {} {}", userId, strategyId, awardId,
                treeStrategyAwardVO.getAwardRuleValue());
        return buildRaffleAwardEntity(strategyId, awardId, treeStrategyAwardVO.getAwardRuleValue());
    }

    private RaffleAwardEntity buildRaffleAwardEntity(Long strategyId, Integer awardId, String awaraConfig) {
        StrategyAwardEntity strategyAwardEntity = strategyRepository.queryStrategyAwardEntity(strategyId,
                awardId);
        return RaffleAwardEntity.builder().awardId(awardId).awardConfig(awaraConfig)
                                .sort(strategyAwardEntity.getSort()).build();
    }

    /**
     * 抽奖计算，责任链抽象方法
     *
     * @param userId     用户ID
     * @param strategyId 策略ID
     * @return 奖品ID
     */
    public abstract DefaultChainFactory.StrategyAwardVO raffleLogicChain(String userId, Long strategyId);

    /**
     * 抽奖结果过滤，决策树抽象方法
     *
     * @param userId     用户ID
     * @param strategyId 策略ID
     * @param awardId    奖品ID
     * @return 过滤结果【奖品ID，会根据抽奖次数判断、库存判断、兜底兜里返回最终的可获得奖品信息】
     */
    public abstract DefaultTreeFactory.StrategyAwardVO raffleLogicTree(String userId, Long strategyId,
                                                                       Integer awardId);

}