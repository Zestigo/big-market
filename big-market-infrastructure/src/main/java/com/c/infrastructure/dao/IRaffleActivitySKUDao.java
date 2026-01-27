package com.c.infrastructure.dao;

import com.c.infrastructure.po.RaffleActivitySKU;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface IRaffleActivitySKUDao {
    RaffleActivitySKU queryActivitySku(Long sku);
}
