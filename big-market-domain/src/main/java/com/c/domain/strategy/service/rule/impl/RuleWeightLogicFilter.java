package com.c.domain.strategy.service.rule.impl;

import com.c.domain.strategy.model.entity.RuleActionEntity;
import com.c.domain.strategy.model.entity.RuleMatterEntity;
import com.c.domain.strategy.model.vo.RuleLogicCheckTypeVO;
import com.c.domain.strategy.repository.IStrategyRepository;
import com.c.domain.strategy.service.annotation.LogicStrategy;
import com.c.domain.strategy.service.rule.ILogicFilter;
import com.c.domain.strategy.service.rule.factory.DefaultLogicFactory;
import com.c.types.common.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.*;

@Slf4j
@Component
@LogicStrategy(logicMode = DefaultLogicFactory.LogicModel.RULE_WEIGHT)
public class RuleWeightLogicFilter implements ILogicFilter<RuleActionEntity.RaffleBeforeEntity> {

    @Resource
    private IStrategyRepository repository;

    /** 临时硬编码：模拟当前用户的积分。在生产环境中，这应该通过 userId 从数据库或缓存中查询。 */
    private Long userScore = 4500L;

    /**
     * 权重规则过滤流程
     * 1. 获取规则配置 (如: 4000:101,102 5000:101,102,103)
     * 2. 根据用户积分寻找其能落入的“最高档位”
     * 3. 如果找到档位，则接管(TAKE_OVER)后续抽奖，限制在特定奖品池中抽奖
     */
    @Override
    public RuleActionEntity<RuleActionEntity.RaffleBeforeEntity> filter(RuleMatterEntity ruleMatterEntity) {
        String userId = ruleMatterEntity.getUserId();
        Long strategyId = ruleMatterEntity.getStrategyId();
        String ruleModel = ruleMatterEntity.getRuleModel();

        log.info("【规则过滤-权重范围】开始。userId:{} strategyId:{} ruleModel:{}", userId, strategyId, ruleModel);

        // 1. 查询数据库中配置的规则值
        String ruleValue = repository.queryStrategyRuleValue(strategyId, ruleMatterEntity.getAwardId(), ruleModel);

        /* * 【线程安全】：
         * 此处的 ruleValueMap 是通过 getAnalyticalValue 方法内部 new 出来的。
         * 它属于“局部变量”，引用存在于每个线程独立的“虚拟机栈”中（即栈封闭）。
         * 即使有 1000 个用户同时抽奖，每个线程拿到的都是自己专属的 TreeMap 实例，
         * 线程之间不存在资源竞争，因此使用非线程安全的 TreeMap 是完全安全的。
         */
        NavigableMap<Long, String> ruleValueMap = getAnalyticalValue(ruleValue);

        // 3. 兜底判断：如果没有配置规则或解析失败，直接放行 (ALLOW)
        if (null == ruleValueMap || ruleValueMap.isEmpty()) {
            return RuleActionEntity.<RuleActionEntity.RaffleBeforeEntity>builder().code(RuleLogicCheckTypeVO.ALLOW.getCode())
                                   .info(RuleLogicCheckTypeVO.ALLOW.getInfo()).build();
        }
        // 2. 将字符串解析为有序映射。Key: 积分档位, Value: 对应奖品串
        // 使用 NavigableMap 的 floorKey(K key) 方法。
        // 该方法返回“小于或等于给定键的最大键”，内部基于红黑树实现，时间复杂度为 O(logN)。
        // 相比于手动写二分查找数组，这种方式代码更简洁，易于维护。
        /* * 4. 核心二分算法：找到小于等于用户当前积分的最大 Key。
         * 场景模拟：
         * 规则档位：{4000, 5000, 6000}
         * 用户积分：4500
         * floorKey(4500) 将返回 4000
         */
        Long matchedKey = ruleValueMap.floorKey(userScore);

        // 5. 如果找到了匹配的档位（即用户积分达到了最低要求的档位）
        if (null != matchedKey) {
            log.info("【规则过滤-权重范围】命中档位：{}，用户当前积分：{}", matchedKey, userScore);
            return RuleActionEntity.<RuleActionEntity.RaffleBeforeEntity>builder()
                                   .data(RuleActionEntity.RaffleBeforeEntity.builder().strategyId(strategyId)
                                                                            .ruleWeightValueKey(ruleValueMap.get(matchedKey)) // 返回该档位对应的奖品范围
                                                                            .build()).ruleModel(DefaultLogicFactory.LogicModel.RULE_WEIGHT.getCode())
                                   .code(RuleLogicCheckTypeVO.TAKE_OVER.getCode()) // “接管”后续流程，按此权重库抽奖
                                   .info(RuleLogicCheckTypeVO.TAKE_OVER.getInfo()).build();
        }

        // 6. 用户积分不足以进入任何权重档位，直接放行，按原始策略抽奖
        return RuleActionEntity.<RuleActionEntity.RaffleBeforeEntity>builder().code(RuleLogicCheckTypeVO.ALLOW.getCode())
                               .info(RuleLogicCheckTypeVO.ALLOW.getInfo()).build();
    }

    /**
     * 解析规则字符串。
     * 待解析格式："4000:102,103 5000:102,103,104"
     * * @return 这里的 TreeMap 会根据 Key (积分) 自动进行升序排列，方便后续进行范围查找。
     * 【设计思考】：
     * * 如果未来需要性能优化（避免每次请求都解析字符串），可以将解析结果放入“缓存”。
     * * 届时如果涉及多线程共享 Map，则需要将 TreeMap 替换为 ConcurrentSkipListMap。
     */
    private NavigableMap<Long, String> getAnalyticalValue(String ruleValue) {
        if (ruleValue == null || ruleValue.isEmpty()) return null;

        // 按空格拆分出多个档位组
        String[] ruleValueGroups = ruleValue.split(Constants.SPACE);
        NavigableMap<Long, String> ruleValueMap = new TreeMap<>();

        for (String group : ruleValueGroups) {
            if (group == null || group.isEmpty()) continue;

            // 按冒号拆分。parts[0]是积分，parts[1]是奖品列表
            String[] parts = group.split(Constants.COLON);
            if (parts.length != 2) {
                throw new IllegalArgumentException("权重规则格式错误，期待 '积分:奖品'，实际收到: " + group);
            }

            // 解析 Key 为 Long 型，用于数值比较
            ruleValueMap.put(Long.parseLong(parts[0]), group);
        }
        return ruleValueMap;
    }
}