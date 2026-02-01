package com.c.domain.activity.service.armory;

import java.util.Date;

/**
 * 活动调度分发接口 (Activity Dispatch Interface)
 * 1. 实时调度：在活动进行阶段，负责高频业务指令（如库存预扣减）的快速分发。
 * 2. 压力缓冲：作为领域层对外的性能窗口，通过直接操作缓存数据，规避高并发流量对数据库的冲击。
 * 3. 资源锁控：通过时间窗口参数控制资源的生命周期，保障分布式环境下的数据安全性。
 *
 * @author cyh
 * @date 2026/02/01
 */
public interface IActivityDispatch {

    /**
     * 执行活动 SKU 缓存库存的原子扣减
     * * 业务背景：
     * 这是高并发抽奖链路中的核心动作。在用户下单参与活动前，先从 Redis 计数器中扣减对应 SKU 的库存。
     * 若扣减成功，则代表用户获得了下单资格，随后由异步流程同步至数据库。
     *
     * @param sku         活动商品库存单元唯一标识。
     * @param endDateTime 活动结束时间。
     *                    作用：1. 用于计算分布式锁或库存 Key 的生存周期（TTL）。
     *                    2. 确保在活动结束后，相关的缓存资源能自动释放，防止内存泄露。
     * @return boolean    扣减结果反馈：
     * - true:  库存充足且扣减成功，用户可以继续执行下单流程。
     * - false: 库存已耗尽（售罄）或活动已过期，需拦截请求并反馈用户。
     */
    boolean subtractionActivitySkuStock(Long sku, Date endDateTime);

}