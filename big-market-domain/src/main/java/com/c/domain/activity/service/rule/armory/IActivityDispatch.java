package com.c.domain.activity.service.rule.armory;

import java.util.Date;

/**
 * 活动调度服务接口
 * 负责活动进行中的实时操作，如高频的库存扣减、状态校验等。
 *
 * @author cyh
 * @date 2026/01/29
 */
public interface IActivityDispatch {

    /**
     * 原子扣减活动 SKU 库存
     *
     * @param sku         商品 SKU 编号
     * @param endDateTime 活动结束时间（用于控制缓存生命周期，防止锁死或数据过期）
     * @return true - 扣减成功（库存充足）；false - 扣减失败（库存不足或已售罄）
     */
    boolean subtractionActivitySkuStock(Long sku, Date endDateTime);

}