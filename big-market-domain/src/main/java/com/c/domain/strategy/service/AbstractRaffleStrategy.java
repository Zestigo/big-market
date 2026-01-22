package com.c.domain.strategy.service;

import com.c.domain.strategy.model.entity.*;
import com.c.domain.strategy.model.vo.RuleLogicCheckTypeVO;
import com.c.domain.strategy.model.vo.StrategyAwardRuleModelVO;
import com.c.domain.strategy.repository.IStrategyRepository;
import com.c.domain.strategy.service.armory.IStrategyDispatch;
import com.c.domain.strategy.service.rule.chain.ILogicChain;
import com.c.domain.strategy.service.rule.chain.factory.DefaultChainFactory;
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
public abstract class AbstractRaffleStrategy implements IRaffleStrategy {

    /** 策略仓储服务：负责策略配置、奖品元数据、规则模型等数据的查询与持久化 */
    protected IStrategyRepository strategyRepository;

    /** 策略调度服务：执行概率算法的核心入口，负责从装配好的奖品池中随机获取奖品 ID */
    protected IStrategyDispatch strategyDispatch;

    /** 责任链工厂：用于根据策略配置动态组装逻辑校验链条（如：黑名单节点 -> 权重节点 -> 默认节点） */
    private final DefaultChainFactory defaultChainFactory;

    public AbstractRaffleStrategy(IStrategyRepository strategyRepository, IStrategyDispatch strategyDispatch,
                                  DefaultChainFactory defaultChainFactory) {
        this.strategyRepository = strategyRepository;
        this.strategyDispatch = strategyDispatch;
        this.defaultChainFactory = defaultChainFactory;
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
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
        }

        // 2. 获取并执行责任链逻辑
        // 通过工厂开启对应策略的逻辑链，执行从“黑名单”到“默认抽奖”的全链路判定
        ILogicChain logicChain = defaultChainFactory.openLogicChain(strategyId);
        Integer awardId = logicChain.logic(userId, strategyId);

        // 3. 抽奖中规则处理（如：抽奖锁、奖品限额等）
        // 3.1 查询该奖品关联的“抽奖中”规则模型
        StrategyAwardRuleModelVO strategyAwardRuleModelVO = strategyRepository.queryStrategyAwardRuleModel(strategyId, awardId);

        // 3.2 执行抽奖中逻辑校验（逻辑钩子由子类实现）
        RuleActionEntity<RuleActionEntity.RaffleCenterEntity> raffleCenterLogic = this.doCheckRaffleCenterLogic(RaffleFactorEntity
                .builder().userId(userId).strategyId(strategyId).awardId(awardId)
                .build(), strategyAwardRuleModelVO.raffleCenterRuleModelList());

        // 3.3 判断校验结果：若触发接管（如被抽奖锁拦截），则返回兜底处理
        if (RuleLogicCheckTypeVO.TAKE_OVER.getCode().equals(raffleCenterLogic.getCode())) {
            log.info("【抽奖中规则接管】用户: {} 命中规则拦截（如抽奖锁），返回规则定义的兜底或空信息", userId);
            return RaffleAwardEntity.builder().awardDesc("【抽奖中规则接管】规则拦截，直接返回结果").build();
        }

        // 4. 封装并返回最终中奖信息
        return RaffleAwardEntity.builder().awardId(awardId).build();
    }

    /**
     * 逻辑钩子：抽奖前置规则校验（由具体业务实现类完成逻辑填装）
     *
     * @param build  抽奖上下文物料
     * @param logics 待执行的规则模型列表
     * @return 规则执行动作决策
     */
    protected abstract RuleActionEntity<RuleActionEntity.RaffleBeforeEntity> doCheckRaffleBeforeLogic(RaffleFactorEntity build,
                                                                                                      String... logics);

    /**
     * 逻辑钩子：抽奖中规则校验（如：库存、奖品锁等）
     *
     * @param build  抽奖上下文物料
     * @param logics 待执行的规则模型列表
     * @return 规则执行动作决策
     */
    protected abstract RuleActionEntity<RuleActionEntity.RaffleCenterEntity> doCheckRaffleCenterLogic(RaffleFactorEntity build,
                                                                                                      String... logics);

    /**
     * 逻辑钩子：抽奖后置规则校验（如：积分翻倍、发放通知等）
     *
     * @param build  抽奖上下文物料
     * @param logics 待执行的规则模型列表
     * @return 规则执行动作决策
     */
    protected abstract RuleActionEntity<RuleActionEntity.RaffleAfterEntity> doCheckRaffleAfterLogic(RaffleFactorEntity build,
                                                                                                    String... logics);

}