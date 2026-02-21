package com.c.domain.strategy.service.rule.chain.impl;

import com.c.domain.strategy.repository.IStrategyRepository;
import com.c.domain.strategy.service.rule.chain.AbstractLogicChain;
import com.c.domain.strategy.service.rule.chain.factory.DefaultChainFactory;
import com.c.types.common.Constants;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 抽奖责任链 - 黑名单过滤节点
 * 逻辑：判断用户是否在黑名单中，若在黑名单则直接返回配置的奖品ID（接管），否则流转至下一节点。
 *
 * @author cyh
 * @date 2026/02/21
 */
@Slf4j
@Component("rule_blacklist")
public class BlackListLogicChain extends AbstractLogicChain {

    /* 策略仓储服务 */
    @Resource
    private IStrategyRepository strategyRepository;

    @Override
    public DefaultChainFactory.StrategyAwardVO logic(String userId, Long strategyId) {
        String ruleModel = ruleModel();
        log.info("抽奖责任链-黑名单处理开始 userId: {}, strategyId: {}, ruleModel: {}", userId, strategyId, ruleModel);

        // 1. 获取规则配置值 (例如: "101:user001,user002")
        String ruleValue = strategyRepository.queryStrategyRuleValue(strategyId, ruleModel);
        if (StringUtils.isBlank(ruleValue)) {
            log.info("抽奖责任链-黑名单规则未配置，直接放行. strategyId: {}", strategyId);
            return nextLogic(userId, strategyId);
        }

        // 2. 解析规则配置并进行防御性校验
        String[] configParts = ruleValue.split(Constants.COLON);
        if (configParts.length < 2) {
            log.error("黑名单规则配置格式错误，请检查: {}", ruleValue);
            return nextLogic(userId, strategyId);
        }

        Integer awardId = Integer.parseInt(configParts[0]);
        String[] blackListUsers = configParts[1].split(Constants.SPLIT);

        // 3. 校验用户是否命中黑名单
        for (String blackUserId : blackListUsers) {
            if (userId.equals(blackUserId)) {
                log.info("抽奖责任链-黑名单命中，截断流程返回奖品. userId: {}, strategyId: {}, awardId: {}", userId, strategyId, awardId);
                return DefaultChainFactory.StrategyAwardVO
                        .builder()
                        .awardId(awardId)
                        .logicModel(ruleModel())
                        .build();
            }
        }

        // 4. 未命中黑名单，流转至下一责任链节点
        log.info("抽奖责任链-黑名单放行 userId: {}, strategyId: {}", userId, strategyId);
        return nextLogic(userId, strategyId);
    }

    /**
     * 封装下一节点逻辑调用
     * 职责：增加非空校验，防止因责任链配置问题导致 next().logic() 触发 NPE。
     */
    private DefaultChainFactory.StrategyAwardVO nextLogic(String userId, Long strategyId) {
        if (null == next()) {
            log.error("【严重异常】责任链断裂，黑名单节点后找不到后续处理节点. strategyId: {}", strategyId);
            return null;
        }
        return next().logic(userId, strategyId);
    }

    @Override
    protected String ruleModel() {
        return DefaultChainFactory.LogicModel.RULE_BLACKLIST.getCode();
    }
}