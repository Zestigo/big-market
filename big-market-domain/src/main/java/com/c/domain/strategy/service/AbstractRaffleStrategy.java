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
 * 抽奖策略核心抽象基类
 * * 定义抽奖的标准算法骨架，编排“抽奖前、抽奖中、抽奖后”的完整流程：
 * 1. 前置处理：通过 [责任链] 过滤黑名单、权重等规则。
 * 2. 核心抽奖：执行随机概率算法抽取奖品。
 * 3. 后置处理：通过 [决策树] 校验库存、次数限制或执行兜底逻辑。
 *
 * @author cyh
 * @since 2026/01/18
 */
@Slf4j
public abstract class AbstractRaffleStrategy implements IRaffleStrategy, IRaffleStock {

    /** 策略仓储：提供配置元数据及概率查找表 */
    protected IStrategyRepository strategyRepository;

    /** 策略调度：执行具体的概率算法 */
    protected IStrategyDispatch strategyDispatch;

    /** 责任链工厂：组装前置过滤链路 */
    protected final DefaultChainFactory defaultChainFactory;

    /** 决策树工厂：编排中后置复杂决策逻辑 */
    protected final DefaultTreeFactory defaultTreeFactory;

    public AbstractRaffleStrategy(IStrategyRepository strategyRepository, IStrategyDispatch strategyDispatch,
                                  DefaultChainFactory defaultChainFactory, DefaultTreeFactory defaultTreeFactory) {
        this.strategyRepository = strategyRepository;
        this.strategyDispatch = strategyDispatch;
        this.defaultChainFactory = defaultChainFactory;
        this.defaultTreeFactory = defaultTreeFactory;
    }

    /**
     * 执行抽奖核心逻辑流程
     *
     * @param raffleFactorEntity 抽奖因子（包含用户、策略、时效信息）
     * @return 最终中奖结果
     */
    @Override
    public RaffleAwardEntity performRaffle(RaffleFactorEntity raffleFactorEntity) {
        // 1. 参数校验
        String userId = raffleFactorEntity.getUserId();
        Long strategyId = raffleFactorEntity.getStrategyId();
        Date endDateTime = raffleFactorEntity.getEndDateTime();

        if (null == strategyId || StringUtils.isBlank(userId)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER);
        }

        // 2. 责任链阶段：执行前置过滤（如黑名单、权重校验）
        DefaultChainFactory.StrategyAwardVO chainStrategyAwardVO = raffleLogicChain(userId, strategyId);
        Integer awardId = chainStrategyAwardVO.getAwardId();

        log.info("抽奖策略-责任链结束 userId:{} strategyId:{} awardId:{} logicModel:{}", userId, strategyId, awardId,
                chainStrategyAwardVO.getLogicModel());

        // 若非默认随机逻辑（如命中黑名单），直接接管结果并返回，不再走后续决策树
        if (!DefaultChainFactory.LogicModel.RULE_DEFAULT
                .getCode()
                .equals(chainStrategyAwardVO.getLogicModel())) {
            return buildRaffleAwardEntity(strategyId, awardId, chainStrategyAwardVO.getAwardRuleValue());
        }

        // 3. 决策树阶段：执行中后置校验（如库存、次数限制、兜底规则）
        DefaultTreeFactory.StrategyAwardVO treeStrategyAwardVO = raffleLogicTree(userId, strategyId, awardId,
                endDateTime);
        awardId = treeStrategyAwardVO.getAwardId();

        log.info("抽奖策略-决策树结束 userId:{} strategyId:{} awardId:{} ruleValue:{}", userId, strategyId, awardId,
                treeStrategyAwardVO.getAwardRuleValue());

        // 4. 封装并返回最终结果
        return buildRaffleAwardEntity(strategyId, awardId, treeStrategyAwardVO.getAwardRuleValue());
    }

    /**
     * 封装奖品实体对象
     */
    private RaffleAwardEntity buildRaffleAwardEntity(Long strategyId, Integer awardId, String awardConfig) {
        StrategyAwardEntity strategyAwardEntity = strategyRepository.queryStrategyAwardEntity(strategyId, awardId);
        return RaffleAwardEntity
                .builder()
                .awardId(awardId)
                .awardConfig(awardConfig)
                .awardTitle(strategyAwardEntity.getAwardTitle())
                .sort(strategyAwardEntity.getSort())
                .build();
    }

    /** 执行责任链逻辑（由子类实现） */
    public abstract DefaultChainFactory.StrategyAwardVO raffleLogicChain(String userId, Long strategyId);

    /** 执行决策树逻辑（由子类实现） */
    public abstract DefaultTreeFactory.StrategyAwardVO raffleLogicTree(String userId, Long strategyId, Integer awardId);

    /** 执行带时效校验的决策树逻辑（由子类实现） */
    public abstract DefaultTreeFactory.StrategyAwardVO raffleLogicTree(String userId, Long strategyId,
                                                                       Integer awardId, Date endDateTime);

}