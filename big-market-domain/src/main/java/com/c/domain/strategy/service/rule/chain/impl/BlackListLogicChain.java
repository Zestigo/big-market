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
 * 配置格式示例："101:user001,user002,user003" (奖品ID:用户ID列表)
 *
 * @author cyh
 * @date 2026/01/18
 */
@Slf4j
@Component("rule_blacklist")
public class BlackListLogicChain extends AbstractLogicChain {

    @Resource
    private IStrategyRepository strategyRepository;

    @Override
    public DefaultChainFactory.StrategyAwardVO logic(String userId, Long strategyId) {
        String ruleModel = ruleModel();
        log.info("抽奖责任链-黑名单处理开始 userId: {}, strategyId: {}, ruleModel: {}", userId, strategyId, ruleModel);

        // 1. 获取规则配置值 (例如: "101:u01,u02")
        String ruleValue = strategyRepository.queryStrategyRuleValue(strategyId, ruleModel);
        if (StringUtils.isBlank(ruleValue)) {
            log.warn("抽奖责任链-黑名单规则配置为空，自动放行 userId: {}, strategyId: {}", userId, strategyId);
            return next().logic(userId, strategyId);
        }

        // 2. 解析规则配置：奖品ID与黑名单列表
        String[] configParts = ruleValue.split(Constants.COLON);
        Integer awardId = Integer.parseInt(configParts[0]);
        String[] blackListUsers = configParts[1].split(Constants.SPLIT);

        // 3. 校验用户是否命中黑名单
        for (String blackUserId : blackListUsers) {
            if (userId.equals(blackUserId)) {
                log.info("抽奖责任链-黑名单命中，直接返回接管奖品 userId: {}, strategyId: {}, awardId: {}", userId, strategyId, awardId);
                return DefaultChainFactory.StrategyAwardVO.builder().awardId(awardId).logicModel(ruleModel()).build();
            }
        }

        // 4. 过滤未命中，流转至下一责任链节点
        log.info("抽奖责任链-黑名单放行 userId: {}, strategyId: {}", userId, strategyId);
        return next().logic(userId, strategyId);
    }

    @Override
    protected String ruleModel() {
        return DefaultChainFactory.LogicModel.RULE_BLACKLIST.getCode();
    }
}