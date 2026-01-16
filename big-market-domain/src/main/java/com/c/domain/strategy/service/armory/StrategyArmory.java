package com.c.domain.strategy.service.armory;

import com.c.domain.strategy.model.entity.StrategyAwardEntity;
import com.c.domain.strategy.repository.IStrategyRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

@Slf4j // 日志注解
@Service // Spring服务注解，交给容器管理
public class StrategyArmory implements IStrategyArmory {
    @Resource // 注入仓储接口（依赖倒置：只依赖接口）
    private IStrategyRepository repository;

    @Override
    public void assembleLotteryStrategy(Long strategyId) {
        // 1. 查询该策略下的所有奖项（先查Redis，再查数据库，由仓储实现层处理）
        List<StrategyAwardEntity> strategyAwardEntities = repository.queryStrategyAwardList(strategyId);

        // 2. 计算概率相关：把“小数概率”转换为“整数范围”（方便随机抽取）
        // 最小奖项概率（比如0.01）
        BigDecimal minAwardRate =
                strategyAwardEntities.stream().map(StrategyAwardEntity::getAwardRate).min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        // 总概率（比如所有奖项概率加起来是1.0）
        BigDecimal totalAwardRate = strategyAwardEntities.stream().map(StrategyAwardEntity::getAwardRate).reduce(BigDecimal.ZERO, BigDecimal::add);
        // 概率范围（总概率/最小概率，向上取整，比如1.0/0.01=100）
        BigDecimal rateRange = totalAwardRate.divide(minAwardRate, 0, RoundingMode.CEILING);

        // 3. 构建概率查找表：按概率分配索引（比如概率0.05=占5个索引）
        List<Integer> strategyAwardSearchRateTables = new ArrayList<>(rateRange.intValue());
        for (StrategyAwardEntity strategyAward : strategyAwardEntities) {
            Integer awardId = strategyAward.getAwardId();
            BigDecimal awardRate = strategyAward.getAwardRate();
            // 计算该奖项占的索引数量（比如0.05*100=5个）
            int count = rateRange.multiply(awardRate).setScale(0, RoundingMode.CEILING).intValue();
            for (int i = 0; i < count; i++) {
                strategyAwardSearchRateTables.add(awardId); // 重复添加，体现概率
            }
        }

        // 4. 打乱列表：保证抽奖随机性（比如5个一等奖索引分散在100个位置中）
        Collections.shuffle(strategyAwardSearchRateTables);

        // 5. 转换为HashMap（key=索引，value=奖品ID），存入Redis
        HashMap<Integer, Integer> shuffleStrategyAwardSearchRateTables = new HashMap<>();
        for (int i = 0; i < strategyAwardSearchRateTables.size(); i++) {
            shuffleStrategyAwardSearchRateTables.put(i, strategyAwardSearchRateTables.get(i));
        }
        repository.storeStrategyAwardSearchRateTables(strategyId, shuffleStrategyAwardSearchRateTables.size(), shuffleStrategyAwardSearchRateTables);
    }

    @Override
    public Integer getRandomAwardId(Long strategyId) {
        // 1. 获取概率范围（比如100）
        int rateRange = repository.getRateRange(strategyId);
        // 2. 生成0~rateRange-1的随机数（比如35）
        int randomKey = new SecureRandom().nextInt(rateRange);
        // 3. 根据随机数从Redis获取奖品ID（比如35对应二等奖）
        return repository.getStrategyAwardAssemble(strategyId, randomKey);
    }
}