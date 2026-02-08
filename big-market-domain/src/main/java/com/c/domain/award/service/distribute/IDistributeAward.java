package com.c.domain.award.service.distribute;

import com.c.domain.award.model.entity.DistributeAwardEntity;

/**
 * @description 分发奖品服务接口
 * @author cyh
 * @date 2026/02/07
 */
public interface IDistributeAward {

    /**
     * 执行奖品分发发放
     *
     * @param distributeAwardEntity 分发奖品实体对象
     */
    void giveOutPrizes(DistributeAwardEntity distributeAwardEntity);

}