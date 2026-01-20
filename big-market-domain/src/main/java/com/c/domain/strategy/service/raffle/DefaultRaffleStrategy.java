package com.c.domain.strategy.service.raffle;

import com.c.domain.strategy.model.entity.RaffleFactorEntity;
import com.c.domain.strategy.model.entity.RuleActionEntity;
import com.c.domain.strategy.model.entity.RuleMatterEntity;
import com.c.domain.strategy.model.vo.RuleLogicCheckTypeVO;
import com.c.domain.strategy.repository.IStrategyRepository;
import com.c.domain.strategy.service.AbstractRaffleStrategy;
import com.c.domain.strategy.service.armory.IStrategyDispatch;
import com.c.domain.strategy.service.rule.chain.factory.DefaultChainFactory;
import com.c.domain.strategy.service.rule.filter.ILogicFilter;
import com.c.domain.strategy.service.rule.filter.factory.DefaultLogicFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @description 默认抽奖策略实现类
 * 该类继承自抽奖抽象类，负责实现具体的“抽奖前、中、后”过滤逻辑编排。
 * 核心职责：将离散的业务规则（如黑名单、权重、会员等级等）按照既定优先级组织起来并触发校验。
 */
@Slf4j
@Service
public class DefaultRaffleStrategy extends AbstractRaffleStrategy {

    /**
     * 逻辑工厂：封装了所有 ILogicFilter 过滤器的实例
     * 通过工厂模式解耦规则的“使用”与“实现”，便于后续横向扩展新的规则模型
     */
    @Resource
    private DefaultLogicFactory logicFactory;

    public DefaultRaffleStrategy(IStrategyRepository repository, IStrategyDispatch strategyDispatch,
                                 DefaultChainFactory defaultChainFactory) {
        super(repository, strategyDispatch, defaultChainFactory);
    }

    /**
     * 【核心编排逻辑】执行抽奖前的规则校验（前置过滤）
     * * 业务场景举例：
     * 1. 命中黑名单 -> 直接拦截，不许抽奖
     * 2. 命中权重规则 -> 改变抽奖范围（接管后续流程），去特定奖池抽奖
     * 3. 校验通过(ALLOW) -> 继续执行下一个过滤器或开始常规抽奖
     *
     * @param raffleFactor 抽奖因子实体，封装了参与者身份（userId）和选用的策略（strategyId）
     * @param logics       从数据库/配置中读取到的该策略关联的规则编码数组，如 ["rule_blacklist", "rule_weight"]
     * @return RuleActionEntity 包含处理动作（ALLOW-放行、TAKE_OVER-接管、INTERCEPT-拦截）及结果数据
     */
    @Override
    protected RuleActionEntity<RuleActionEntity.RaffleBeforeEntity> doCheckRaffleBeforeLogic(RaffleFactorEntity raffleFactor,
                                                                                             String... logics) {
        // 0. 安全兜底
        if (logics == null || logics.length == 0)
            return RuleActionEntity.<RuleActionEntity.RaffleBeforeEntity>builder().code(RuleLogicCheckTypeVO.ALLOW.getCode())
                                   .info(RuleLogicCheckTypeVO.ALLOW.getInfo()).build();

        // 1. 获取过滤器 Map
        Map<String, ILogicFilter<RuleActionEntity.RaffleBeforeEntity>> logicFilterGroup = logicFactory.openLogicFilter();

        // 2. 规则排序
        // 注意：这里直接对 logics 进行 stream 即可，不再需要 split，因为数组已经是由 strategy.ruleModels() 处理好的干净数据
        List<String> sortedLogics = Arrays.stream(logics).filter(StringUtils::isNotBlank)
                                          .sorted(Comparator.comparing(logic -> logic.equals(DefaultLogicFactory.LogicModel.RULE_BLACKLIST.getCode()) ? 0 : 1))
                                          .collect(Collectors.toList());

        // 3. 链式执行过滤器
        RuleActionEntity<RuleActionEntity.RaffleBeforeEntity> lastResult = null;

        for (String ruleModel : sortedLogics) {
            ILogicFilter<RuleActionEntity.RaffleBeforeEntity> logicFilter = logicFilterGroup.get(ruleModel);

            // 健壮性保护
            if (logicFilter == null) {
                log.warn("【警告】未找到对应的规则过滤器实现，请检查配置或代码注入: {}", ruleModel);
                continue;
            }

            // 4. 组装决策物料
            RuleMatterEntity ruleMatterEntity = RuleMatterEntity.builder().userId(raffleFactor.getUserId())
                                                                .strategyId(raffleFactor.getStrategyId()).ruleModel(ruleModel)
                                                                .build();
            // 5. 执行过滤校验
            lastResult = logicFilter.filter(ruleMatterEntity);
            log.info("抽奖前规则过滤流程: 用户={} 规则={} 结果状态={}", raffleFactor.getUserId(), ruleModel, lastResult.getCode());

            // 6. 流程状态决策机：非 ALLOW 则立即返回（接管或拦截）
            if (!RuleLogicCheckTypeVO.ALLOW.getCode().equals(lastResult.getCode())) {
                return lastResult;
            }
        }

        return lastResult;
    }

    @Override
    protected RuleActionEntity<RuleActionEntity.RaffleCenterEntity> doCheckRaffleCenterLogic(RaffleFactorEntity raffleFactor,
                                                                                             String... logics) {
        // 0. 安全兜底
        if (logics == null || logics.length == 0)
            return RuleActionEntity.<RuleActionEntity.RaffleCenterEntity>builder().code(RuleLogicCheckTypeVO.ALLOW.getCode())
                                   .info(RuleLogicCheckTypeVO.ALLOW.getInfo()).build();
        // 1. 获取过滤器 Map
        Map<String, ILogicFilter<RuleActionEntity.RaffleCenterEntity>> logicFilterGroup = logicFactory.openLogicFilter();
        // 3. 链式执行过滤器
        RuleActionEntity<RuleActionEntity.RaffleCenterEntity> lastResult = null;

        for (String ruleModel : logics) {
            ILogicFilter<RuleActionEntity.RaffleCenterEntity> logicFilter = logicFilterGroup.get(ruleModel);
            // 健壮性保护
            if (logicFilter == null) {
                log.warn("【警告】未找到对应的规则过滤器实现，请检查配置或代码注入: {}", ruleModel);
                continue;
            }

            // 4. 组装决策物料
            RuleMatterEntity ruleMatterEntity = RuleMatterEntity.builder().userId(raffleFactor.getUserId())
                                                                .strategyId(raffleFactor.getStrategyId())
                                                                .awardId(raffleFactor.getAwardId()).ruleModel(ruleModel).build();

            lastResult = logicFilter.filter(ruleMatterEntity);
            log.info("抽奖中规则过滤流程: 用户={} 规则={} 结果状态={} 结果描述={}", raffleFactor.getUserId(), ruleModel, lastResult.getCode(),
                    lastResult.getInfo());
            // 6. 流程状态决策机：非 ALLOW 则立即返回（接管或拦截）
            if (!RuleLogicCheckTypeVO.ALLOW.getCode().equals(lastResult.getCode())) {
                return lastResult;
            }
        }
        return lastResult;
    }

    @Override
    protected RuleActionEntity<RuleActionEntity.RaffleAfterEntity> doCheckRaffleAfterLogic(RaffleFactorEntity build,
                                                                                           String... logics) {


        return null;
    }
}