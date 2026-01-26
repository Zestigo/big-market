package com.c.types.common;

import org.apache.shardingsphere.sharding.api.sharding.standard.PreciseShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.RangeShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.StandardShardingAlgorithm;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

/**
 * @author cyh
 * @description 自定义标准分表算法
 * 场景：在库确定的基础上，根据 user_id 进一步细化数据存放的物理表。
 * @date 2026/01/26
 */
@Slf4j
public class MyTableAlgorithm implements StandardShardingAlgorithm<String> {
    @Override
    public String doSharding(Collection<String> availableTargetNames,
                             PreciseShardingValue<String> shardingValue) {

        String userId = String.valueOf(shardingValue.getValue());
        List<String> tables = availableTargetNames.stream().sorted().collect(Collectors.toList());

        // --- 核心优化：二次哈希（扰动） ---
        // 1. 获取基础 hashCode
        int h = userId.hashCode();

        // 2. 扰动算法：让高 16 位和低 16 位异或，并加上逻辑表名的影响
        // 这样即使 userId 一样，不同的逻辑表（如果以后有别的表）也会有不同的分布
        int hash = (h ^ (h >>> 16)) ^ shardingValue.getLogicTableName().hashCode();

        // 3. 再次确保绝对值并取模
        int index = Math.abs(hash) % tables.size();

        String target = tables.get(index);

        // 增加 DEBUG 日志查看具体的 hash 计算过程
        log.info("[TB-SHARD] userId={} -> rawHash={} -> finalHash={} -> index={} -> target={}", userId, h,
                hash, index, target);

        return target;
    }

    @Override
    public Collection<String> doSharding(Collection<String> availableTargetNames,
                                         RangeShardingValue<String> shardingValue) {
        throw new UnsupportedOperationException("不支持范围查询分表");
    }

    @Override
    public String getType() {
        return "CLASS_BASED";
    }

    @Override
    public void init() {
    }
}