package com.c.domain.strategy.service.raffle;

import com.c.domain.strategy.model.entity.RaffleAwardEntity;
import com.c.domain.strategy.model.entity.RaffleFactorEntity;
import com.c.domain.strategy.model.entity.RuleActionEntity;
import com.c.domain.strategy.model.entity.StrategyEntity;
import com.c.domain.strategy.model.vo.RuleLogicCheckTypeVO;
import com.c.domain.strategy.repository.IStrategyRepository;
import com.c.domain.strategy.service.IRaffleStrategy;
import com.c.domain.strategy.service.armory.IStrategyDispatch;
import com.c.domain.strategy.service.rule.factory.DefaultLogicFactory;
import com.c.types.enums.ResponseCode;
import com.c.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * @author cyh
 * @description 抽奖策略抽象基类
 * 核心架构：采用【模板方法模式】定义抽奖的标准算法骨架。
 * 核心逻辑：将通用的“参数校验”、“元数据查询”、“执行调度”放在基类，而将多变的“规则校验”编排交给子类实现。
 * 这种设计确保了抽奖主流程的稳定性，同时也提供了极强的扩展性（如后续增加会员规则、等级规则等）。
 */
@Slf4j
public abstract class AbstractRaffleStrategy implements IRaffleStrategy {

    /** 策略仓储服务：提供对数据库/缓存中策略、规则、奖品等元数据的访问入口 */
    protected IStrategyRepository strategyRepository;

    /** 策略调度服务：负责执行核心概率算法，从奖品池（Redis/本地缓存）中定位具体的奖品 ID */
    protected IStrategyDispatch strategyDispatch;

    public AbstractRaffleStrategy(IStrategyRepository strategyRepository, IStrategyDispatch strategyDispatch) {
        this.strategyRepository = strategyRepository;
        this.strategyDispatch = strategyDispatch;
    }

    /**
     * 执行抽奖决策的主流程方法
     * 流程：1.参数校验 -> 2.策略检索 -> 3.规则过滤 -> 4.接管判断 -> 5.常规抽奖
     *
     * @param raffleFactorEntity 抽奖因子实体，包含用户ID、策略ID等请求参数
     * @return RaffleAwardEntity 抽奖结果实体，包含最终中的奖品ID及相关信息
     */
    @Override
    public RaffleAwardEntity performRaffle(RaffleFactorEntity raffleFactorEntity) {
        // 1. 参数校验：防御性编程，确保后续逻辑执行的基础数据合法性
        String userId = raffleFactorEntity.getUserId();
        Long strategyId = raffleFactorEntity.getStrategyId();
        if (null == strategyId || StringUtils.isBlank(userId)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
        }

        // 2. 策略元数据查询：从仓储层获取该策略的详细配置（包括该策略开启了哪些规则模型）
        // DDD 设计思考：StrategyEntity 承载了策略的“静态属性”和“业务行为方法”
        StrategyEntity strategy = strategyRepository.queryStrategyEntityByStrategyId(strategyId);

        // 3. 执行【抽奖前】规则过滤逻辑
        // 【关键点】调用 strategy.ruleModels() 而非 getter 方法。
        // ruleModels() 内部会将字符串 "rule_blacklist,rule_weight" 拆分为 String[]。
        // Java 可变参数 (String... logics) 接收到 String[] 时会按元素解开，
        // 从而让子类的 for 循环能逐个处理 "rule_blacklist" 和 "rule_weight"。
        RuleActionEntity<RuleActionEntity.RaffleBeforeEntity> ruleActionEntity = this.doCheckRaffleBeforeLogic(
                RaffleFactorEntity.builder()
                                  .userId(userId)
                                  .strategyId(strategyId)
                                  .build(),
                strategy.ruleModels());

        // 4. 流程接管判断（决策引擎核心分支）
        // ruleActionEntity.getCode() 代表决策动作（ALLOW:放行，TAKE_OVER:接管并直接返回结果）
        if (RuleLogicCheckTypeVO.TAKE_OVER.getCode().equals(ruleActionEntity.getCode())) {

            // 获取具体触发接管的规则标识
            String ruleModel = ruleActionEntity.getRuleModel();

            // 4.1 黑名单规则接管处理：
            // 场景：用户在黑名单内，直接返回规则中配置的特定奖品（如101奖品），不再进行随机概率抽奖。
            if (DefaultLogicFactory.LogicModel.RULE_BLACKLIST.getCode().equals(ruleModel)) {
                log.info("【规则接管-黑名单】用户:{} 命中黑名单，直接返回奖品:{}", userId, ruleActionEntity.getData().getAwardId());
                return RaffleAwardEntity.builder()
                                        .awardId(ruleActionEntity.getData().getAwardId())
                                        .build();
            }

            // 4.2 权重规则接管处理：
            // 场景：用户积分达标，抽奖范围被缩小至特定的权重奖池（如积分>4000，只能在102,103奖品中抽）。
            else if (DefaultLogicFactory.LogicModel.RULE_WEIGHT.getCode().equals(ruleModel)) {
                RuleActionEntity.RaffleBeforeEntity raffleBeforeEntity = ruleActionEntity.getData();
                String ruleWeightValueKey = raffleBeforeEntity.getRuleWeightValueKey();

                // 执行“带权重的随机抽奖”：调度引擎会根据权重Key找到特定的查找表
                log.info("【规则接管-权重范围】用户:{} 命中权重档位，在指定池中抽奖 Key:{}", userId, ruleWeightValueKey);
                Integer awardId = strategyDispatch.getRandomAwardId(strategyId, ruleWeightValueKey);
                return RaffleAwardEntity.builder()
                                        .awardId(awardId)
                                        .build();
            }
        }

        // 5. 执行常规抽奖（默认流程）
        // 场景：未配置规则，或者所有规则均返回 ALLOW（未命中黑名单且积分不满足任何权重档位）。
        // 此时执行基于全量概率表的随机抽奖。
        log.info("【标准抽奖】用户:{} 未命中任何接管规则，执行常规概率抽奖", userId);
        Integer awardId = strategyDispatch.getRandomAwardId(strategyId);

        return RaffleAwardEntity.builder()
                                .awardId(awardId)
                                .build();
    }

    /**
     * 抽象逻辑钩子：执行具体的抽奖前规则校验
     * * @param build  包装了抽奖必须的上下文物料
     * @param logics 可变参数，代表需要执行的规则编码列表
     * @return 规则执行后的动作决策实体
     */
    protected abstract RuleActionEntity<RuleActionEntity.RaffleBeforeEntity> doCheckRaffleBeforeLogic(RaffleFactorEntity build, String... logics);

}