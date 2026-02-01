package com.c.domain.activity.service;

import com.c.domain.activity.model.aggregate.CreateOrderAggregate;
import com.c.domain.activity.model.entity.*;
import com.c.domain.activity.model.vo.ActivitySkuStockKeyVO;
import com.c.domain.activity.model.vo.OrderStateVO;
import com.c.domain.activity.repositor.IActivityRepository;
import com.c.domain.activity.service.rule.factory.DefaultActivityChainFactory;
import org.apache.commons.lang.RandomStringUtils;
import org.springframework.stereotype.Service;

import java.util.Date;

/**
 * 抽奖活动领域服务实现类
 * * 核心职责：
 * 1. 业务流程编排：继承 AbstractRaffleActivity 抽象类，通过模板方法定义“校验-组装-落库”的标准流水线。
 * 2. 库存一致性维护：实现 ISkuStock 接口，作为异步库存流水消费的核心实现，衔接 Redis 预扣与数据库写回逻辑。
 * 3. 聚合根实例化：负责将活动配置实体与用户参与实体转换为 CreateOrderAggregate，确保持久化数据的原子性与完整性。
 *
 * @author cyh
 * @date 2026/01/27
 */
@Service
public class RaffleActivityService extends AbstractRaffleActivity implements ISkuStock {

    /**
     * 构造函数注入：初始化底层组件
     *
     * @param activityRepository          活动仓储：封装对 DB、Redis 及延迟消息队列的底层访问。
     * @param defaultActivityChainFactory 责任链工厂：通过工厂模式获取针对不同 SKU 的业务校验规则链。
     */
    public RaffleActivityService(IActivityRepository activityRepository,
                                 DefaultActivityChainFactory defaultActivityChainFactory) {
        super(activityRepository, defaultActivityChainFactory);
    }


    /**
     * 构建活动订单聚合根
     * * 业务逻辑说明：
     * 1. 实例化 ActivityOrderEntity：将充值请求中的流水信息与活动配置信息合并，作为本次参与的业务凭证。
     * 2. 生成全局流水：目前使用 RandomStringUtils 生成 12 位数字（建议在高并发生产环境下切换至雪花算法 ID）。
     * 3. 状态预设：订单状态初始设为 completed（已完成），表示用户已成功获得参与该活动的合法资格。
     *
     * @return CreateOrderAggregate 包含用户信息、活动信息及订单明细的聚合根
     */
    @Override
    protected CreateOrderAggregate buildOrderAggregate(SkuRechargeEntity skuRechargeEntity,
                                                       ActivitySkuEntity activitySkuEntity,
                                                       ActivityEntity activityEntity,
                                                       ActivityCountEntity activityCountEntity) {

        // 1. 构建活动订单实体：承载业务快照与支付/参与流水
        ActivityOrderEntity activityOrderEntity = ActivityOrderEntity.builder()
                                                                     .userId(skuRechargeEntity.getUserId())
                                                                     .sku(skuRechargeEntity.getSku())
                                                                     .activityId(activityEntity.getActivityId())
                                                                     .activityName(activityEntity.getActivityName())
                                                                     .strategyId(activityEntity.getStrategyId())
                                                                     .outBusinessNo(skuRechargeEntity.getOutBusinessNo())
                                                                     // 订单ID：作为业务主键，关联后续的抽奖流程
                                                                     .orderId(RandomStringUtils.randomNumeric(12))
                                                                     .orderTime(new Date())
                                                                     .totalCount(activityCountEntity.getTotalCount())
                                                                     .dayCount(activityCountEntity.getDayCount())
                                                                     .monthCount(activityCountEntity.getMonthCount())
                                                                     .state(OrderStateVO.completed).build();

        // 2. 组装并返回聚合根：统一管理关联实体的生命周期
        return CreateOrderAggregate.builder().userId(skuRechargeEntity.getUserId())
                                   .activityId(activitySkuEntity.getActivityId())
                                   .totalCount(activityCountEntity.getTotalCount())
                                   .dayCount(activityCountEntity.getDayCount())
                                   .monthCount(activityCountEntity.getMonthCount())
                                   .activityOrderEntity(activityOrderEntity).build();
    }

    /**
     * 执行订单持久化落库
     * * 调用触发点：在责任链（含库存扣减、规则匹配）执行成功后，由父类模板方法发起调用。
     *
     * @param createOrderAggregate 封装了订单流水与参与限额信息的聚合根
     */
    @Override
    protected void doSaveOrder(CreateOrderAggregate createOrderAggregate) {
        activityRepository.doSaveOrder(createOrderAggregate);
    }

    /**
     * 获取异步库存更新队列中的任务
     * * 核心作用：作为 Worker 节点的任务抓取口，实现缓存库存扣减后的异步消息消费。
     *
     * @return ActivitySkuStockKeyVO 包含 SKU 信息的库存更新指令
     * @throws InterruptedException 阻塞获取时的线程中断异常
     */
    @Override
    public ActivitySkuStockKeyVO takeQueueValue() throws InterruptedException {
        return activityRepository.takeQueueValue();
    }


    /**
     * 刷新特定 SKU 的数据库物理库存
     * * 处理逻辑：根据异步队列中的计数结果，通过乐观锁或 row-lock 更新数据库中的 stock 字段。
     *
     * @param sku 活动对应的商品 SKU 标识
     */
    @Override
    public void subtractionActivitySkuStock(Long sku) {
        activityRepository.subtractionActivitySkuStock(sku);
    }

    /**
     * 执行库存清零同步
     * * 触发场景：当 Redis 缓存判定库存售罄时，通过此方法强行修正数据库状态，防止超卖或无效扣减。
     *
     * @param sku 活动对应的商品 SKU 标识
     */
    @Override
    public void zeroOutActivitySkuStock(Long sku) {
        activityRepository.zeroOutActivitySkuStock(sku);
    }

    /**
     * 批量更新 SKU 活动库存（通常用于补偿或初始化）
     * * @param sku   活动商品 SKU
     *
     * @param count 更新的库存差值或目标值
     */
    @Override
    public void updateActivitySkuStockBatch(Long sku, Integer count) {
        activityRepository.updateActivitySkuStockBatch(sku, count);
    }

    /**
     * 设置库存售罄标识（防击穿）
     * * 逻辑说明：在缓存中设置标识位，避免在库存为 0 时仍频繁查询数据库，提升系统响应。
     *
     * @param sku 活动商品 SKU
     */
    @Override
    public void setSkuStockZeroFlag(Long sku) {
        activityRepository.setSkuStockZeroFlag(sku);
    }

    /**
     * 判断当前 SKU 是否已售罄
     * * @param sku 活动商品 SKU
     *
     * @return boolean true 为已售罄
     */
    @Override
    public boolean isSkuStockZero(Long sku) {
        return activityRepository.isSkuStockZero(sku);
    }

}