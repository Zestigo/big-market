package com.c.domain.rebate.service;

import com.c.domain.rebate.model.entity.BehaviorEntity;
import com.c.domain.rebate.model.entity.BehaviorRebateOrderEntity;

import java.util.List;

/**
 * 行为返利领域服务接口
 *
 * @author cyh
 * @date 2026/02/05
 */
public interface IBehaviorRebateService {

    /**
     * 创建行为返利订单
     * 根据用户行为匹配返利规则，生成并持久化返利流水订单。
     *
     * @param behaviorEntity 行为实体（包含用户ID、行为类型、外部业务防重ID）
     * @return List<String> 成功创建的返利订单号列表
     */
    List<String> createOrder(BehaviorEntity behaviorEntity);

    /**
     * 根据外部业务单号查询返利订单
     * 主要用于在执行返利前进行业务幂等校验。
     *
     * @param userId 用户ID
     * @param outBusinessNo 外部业务唯一单号
     * @return List<BehaviorRebateOrderEntity> 行为返利订单实体列表
     */
    List<BehaviorRebateOrderEntity> queryOrderByOutBusinessNo(String userId, String outBusinessNo);

}