package com.c.domain.activity.service.quota;

import com.c.domain.activity.model.aggregate.CreateQuotaOrderAggregate;
import com.c.domain.activity.model.entity.*;
import com.c.domain.activity.model.vo.ActivitySkuStockKeyVO;
import com.c.domain.activity.model.vo.OrderStateVO;
import com.c.domain.activity.repository.IActivityRepository;
import com.c.domain.activity.service.IRaffleActivitySkuStockService;
import com.c.domain.activity.service.quota.policy.ITradePolicy;
import com.c.domain.activity.service.quota.rule.factory.DefaultActivityChainFactory;
import org.apache.commons.lang.RandomStringUtils;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Map;

/**
 * 抽奖活动账户额度领域服务
 * 负责业务流程编排、库存一致性维护以及额度订单聚合根的构建。
 *
 * @author cyh
 * @date 2026/01/27
 */
@Service
public class RaffleActivityAccountQuotaService extends AbstractRaffleActivityAccountQuota implements IRaffleActivitySkuStockService {

    /**
     * 构造方法，注入核心依赖
     *
     * @param activityRepository          活动仓储接口
     * @param defaultActivityChainFactory 活动规则责任链工厂
     * @param tradePolicyGroup            交易策略映射组
     */
    public RaffleActivityAccountQuotaService(IActivityRepository activityRepository,
                                             DefaultActivityChainFactory defaultActivityChainFactory, Map<String,
                    ITradePolicy> tradePolicyGroup) {
        super(activityRepository, defaultActivityChainFactory, tradePolicyGroup);
    }

    /**
     * 构建额度订单聚合根
     * 将充值请求与活动配置合并，生成全局流水号并预设订单状态。
     *
     * @return 包含用户信息、活动信息及订单明细的聚合根
     */
    @Override
    protected CreateQuotaOrderAggregate buildOrderAggregate(SkuRechargeEntity skuRechargeEntity,
                                                            ActivitySkuEntity activitySkuEntity,
                                                            ActivityEntity activityEntity,
                                                            ActivityCountEntity activityCountEntity) {

        // 构建活动订单实体
        ActivityOrderEntity activityOrderEntity = ActivityOrderEntity
                .builder()
                .userId(skuRechargeEntity.getUserId())
                .sku(skuRechargeEntity.getSku())
                .activityId(activityEntity.getActivityId())
                .activityName(activityEntity.getActivityName())
                .strategyId(activityEntity.getStrategyId())
                .outBusinessNo(skuRechargeEntity.getOutBusinessNo())
                .orderId(RandomStringUtils.randomNumeric(12))
                .orderTime(new Date())
                .totalCount(activityCountEntity.getTotalCount())
                .dayCount(activityCountEntity.getDayCount())
                .payAmount(activitySkuEntity.getPayAmount())
                .monthCount(activityCountEntity.getMonthCount())
                .build();

        // 组装并返回聚合根
        return CreateQuotaOrderAggregate
                .builder()
                .userId(skuRechargeEntity.getUserId())
                .activityId(activitySkuEntity.getActivityId())
                .totalCount(activityCountEntity.getTotalCount())
                .dayCount(activityCountEntity.getDayCount())
                .monthCount(activityCountEntity.getMonthCount())
                .activityOrderEntity(activityOrderEntity)
                .build();
    }

    /**
     * 更新订单状态
     * 通过仓储层对投递单关联的业务订单进行状态变更及后续处理。
     *
     * @param deliveryOrderEntity 投递单实体，包含业务幂等标识
     */
    @Override
    public void updateOrder(DeliveryOrderEntity deliveryOrderEntity) {
        activityRepository.updateOrder(deliveryOrderEntity);
    }

    /**
     * 获取异步库存队列值
     *
     * @return 待更新库存的 SKU 标识
     * @throws InterruptedException 线程中断异常
     */
    @Override
    public ActivitySkuStockKeyVO takeQueueValue() throws InterruptedException {
        return activityRepository.takeQueueValue();
    }

    /**
     * 物理库存异步扣减
     *
     * @param sku 商品 SKU 编号
     */
    @Override
    public void subtractionActivitySkuStock(Long sku) {
        activityRepository.subtractionActivitySkuStock(sku);
    }

    /**
     * 物理库存售罄置零同步
     *
     * @param sku 商品 SKU 编号
     */
    @Override
    public void zeroOutActivitySkuStock(Long sku) {
        activityRepository.zeroOutActivitySkuStock(sku);
    }

    /**
     * 物理库存批量同步更新
     *
     * @param sku   商品 SKU 编号
     * @param count 扣减增量值
     */
    @Override
    public void updateActivitySkuStockBatch(Long sku, Integer count) {
        activityRepository.updateActivitySkuStockBatch(sku, count);
    }

    /**
     * 设置库存售罄标识
     *
     * @param sku 商品 SKU 编号
     */
    @Override
    public void setSkuStockZeroFlag(Long sku) {
        activityRepository.setSkuStockZeroFlag(sku);
    }

    /**
     * 检查库存是否售罄
     *
     * @param sku 商品 SKU 编号
     * @return true 为已售罄
     */
    @Override
    public boolean isSkuStockZero(Long sku) {
        return activityRepository.isSkuStockZero(sku);
    }


    /**
     * 查询用户当日累计参与次数
     *
     * @param activityId 活动唯一标识
     * @param userId     用户唯一标识
     * @return 当日累计参与次数，无记录则返回 0
     */
    @Override
    public Integer queryRaffleActivityAccountDayPartakeCount(Long activityId, String userId) {
        return activityRepository.queryRaffleActivityAccountDayPartakeCount(activityId, userId);
    }

    /**
     * 查询用户活动账户实体
     *
     * @param activityId 活动 ID
     * @param userId     用户唯一 ID
     * @return 活动账户额度实体
     */
    @Override
    public ActivityAccountEntity queryActivityAccountEntity(Long activityId, String userId) {
        return activityRepository.queryActivityAccountEntity(activityId, userId);
    }

    /**
     * 查询用户已参与抽奖总次数
     *
     * @param activityId 活动 ID
     * @param userId     用户 ID
     * @return 已参与次数
     */
    @Override
    public Integer queryRaffleActivityAccountPartakeCount(Long activityId, String userId) {
        return activityRepository.queryRaffleActivityAccountPartakeCount(activityId, userId);
    }
}