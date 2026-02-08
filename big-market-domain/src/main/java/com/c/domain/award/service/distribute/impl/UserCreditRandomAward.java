package com.c.domain.award.service.distribute.impl;

import com.c.domain.award.model.aggregate.GiveOutPrizesAggregate;
import com.c.domain.award.model.entity.DistributeAwardEntity;
import com.c.domain.award.model.entity.UserAwardRecordEntity;
import com.c.domain.award.model.entity.UserCreditAwardEntity;
import com.c.domain.award.model.vo.AwardStateVO;
import com.c.domain.award.repository.IAwardRepository;
import com.c.domain.award.service.distribute.IDistributeAward;
import com.c.types.common.Constants;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @author cyh
 * @description 用户随机积分奖品分发服务
 * @date 2026/02/07
        */
@Component("user_credit_random")
public class UserCreditRandomAward implements IDistributeAward {

    @Resource
    private IAwardRepository awardRepository;

    @Override
    public void giveOutPrizes(DistributeAwardEntity distributeAwardEntity) {
        // 1. 奖品配置处理
        Integer awardId = distributeAwardEntity.getAwardId();
        String awardConfig = distributeAwardEntity.getAwardConfig();
        if (StringUtils.isBlank(awardConfig)) {
            awardConfig = awardRepository.queryAwardConfig(awardId);
        }

        // 2. 积分范围解析
        String[] creditRange = awardConfig.split(Constants.SPLIT);
        if (creditRange.length != 2) {
            throw new RuntimeException("award_config 「" + awardConfig + "」配置错误，示例：1,100");
        }

        // 3. 生成随机积分值
        BigDecimal creditAmount = generateRandom(new BigDecimal(creditRange[0]), new BigDecimal(creditRange[1]));

        // 4. 构建中奖记录实体
        UserAwardRecordEntity userAwardRecordEntity = UserAwardRecordEntity
                .builder()
                .userId(distributeAwardEntity.getUserId())
                .orderId(distributeAwardEntity.getOrderId())
                .awardId(distributeAwardEntity.getAwardId())
                .awardState(AwardStateVO.COMPLETED)
                .build();

        // 5. 构建积分奖品实体
        UserCreditAwardEntity userCreditAwardEntity = UserCreditAwardEntity
                .builder()
                .userId(distributeAwardEntity.getUserId())
                .creditAmount(creditAmount)
                .build();

        // 6. 构建发奖聚合对象
        GiveOutPrizesAggregate giveOutPrizesAggregate = GiveOutPrizesAggregate
                .builder()
                .userId(distributeAwardEntity.getUserId())
                .userAwardRecordEntity(userAwardRecordEntity)
                .userCreditAwardEntity(userCreditAwardEntity)
                .build();

        // 7. 存储发奖对象
        awardRepository.saveGiveOutPrizesAggregate(giveOutPrizesAggregate);
    }

    /**
     * 生成指定范围内的随机积分
     *
     * @param min 最小值
     * @param max 最大值
     * @return 随机积分值
     */
    private BigDecimal generateRandom(BigDecimal min, BigDecimal max) {
        if (min.compareTo(max) >= 0) return min;

        // 1. 获取线程安全的随机数生成器，并指定随机范围 [min, max]
        // nextDouble 的 bound 是开区间（不含边界），故加 0.01 以确保能随机到 max 值
        double randomValue = ThreadLocalRandom
                .current()
                .nextDouble(min.doubleValue(), max
                        .add(new BigDecimal("0.01"))
                        .doubleValue());

        // 2. 将 double 转回 BigDecimal 并四舍五入保留两位小数
        return new BigDecimal(randomValue).setScale(2, RoundingMode.HALF_UP);
    }
}