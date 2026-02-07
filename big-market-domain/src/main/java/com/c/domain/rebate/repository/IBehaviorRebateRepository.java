package com.c.domain.rebate.repository;

import com.c.domain.rebate.model.aggregate.BehaviorRebateAggregate;
import com.c.domain.rebate.model.entity.BehaviorRebateOrderEntity;
import com.c.domain.rebate.model.vo.BehaviorTypeVO;
import com.c.domain.rebate.model.vo.DailyBehaviorRebateVO;

import java.util.List;

/**
 * 行为返利仓储接口
 *
 * @author cyh
 * @date 2026/02/05
 */
public interface IBehaviorRebateRepository {

    /**
     * 根据行为类型查询对应的每日返利配置列表
     *
     * @param behaviorTypeVO 行为类型枚举（如签到、支付）
     * @return List<DailyBehaviorRebateVO> 返利配置视图对象列表
     */
    List<DailyBehaviorRebateVO> queryDailyBehaviorRebateConfig(BehaviorTypeVO behaviorTypeVO);

    /**
     * 持久化保存用户返利记录
     *
     * @param userId 用户唯一标识 ID
     * @param behaviorRebateAggregates 行为返利聚合记录列表（包含订单流水与补偿任务）
     */
    void saveUserRebateRecord(String userId, List<BehaviorRebateAggregate> behaviorRebateAggregates);

    /**
     * 根据外部业务单号查询返利订单
     *
     * @param userId 用户ID
     * @param outBusinessNo 外部业务唯一单号
     * @return List<BehaviorRebateOrderEntity> 行为返利订单实体列表
     */
    List<BehaviorRebateOrderEntity> queryOrderByOutBusinessNo(String userId, String outBusinessNo);

}