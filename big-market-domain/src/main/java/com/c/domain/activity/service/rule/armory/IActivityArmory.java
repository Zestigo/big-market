package com.c.domain.activity.service.rule.armory;

/**
 * @description 活动装配库接口
 * 职责：负责活动数据的预热与初始化，将数据库中的配置装配到缓存中，为高并发扣减做准备。
 */
public interface IActivityArmory {

    /**
     * 装配活动 SKU 配置与库存预热
     *
     * @param sku 活动商品库存单元 ID
     * @return true - 装配成功（数据已同步至缓存）；false - 装配失败
     */
    boolean assembleActivitySku(Long sku);

}