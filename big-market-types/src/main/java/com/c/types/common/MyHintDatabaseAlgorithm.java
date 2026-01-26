package com.c.types.common;

import lombok.extern.slf4j.Slf4j;
import org.apache.shardingsphere.sharding.api.sharding.hint.HintShardingAlgorithm;
import org.apache.shardingsphere.sharding.api.sharding.hint.HintShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.PreciseShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.RangeShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.StandardShardingAlgorithm;

import java.util.Collection;
import java.util.List;
import java.util.Collections;
import java.util.stream.Collectors;

/**
 * @author cyh
 * @description 自定义 Hint 分库算法
 * 场景：当 SQL 中没有 user_id 字段，但业务明确知道需要操作哪个库时（如后台管理、数据同步），
 * 开发者可以使用 HintManager 手动指定分片值。
 * @date 2026/01/26
 */
@Slf4j
public class MyHintDatabaseAlgorithm implements HintShardingAlgorithm<String>,
        StandardShardingAlgorithm<String> {

    /**
     * 兼容性实现：当 Hint 算法被当作 Standard 算法误用时，逻辑依然有效
     */
    @Override
    public String doSharding(Collection<String> availableTargetNames,
                             PreciseShardingValue<String> shardingValue) {
        return doByValue(availableTargetNames, shardingValue.getValue());
    }

    /**
     * Hint 核心实现
     *
     * @param availableTargetNames 可用目标库
     * @param shardingValue        通过 HintManager.addDatabaseShardingValue 传入的值
     */
    @Override
    public Collection<String> doSharding(Collection<String> availableTargetNames,
                                         HintShardingValue<String> shardingValue) {

        // 如果没有传入任何 Hint 值，则返回所有目标库（会导致全库广播执行，慎用）
        if (shardingValue.getValues() == null || shardingValue.getValues().isEmpty()) {
            return availableTargetNames;
        }

        // 获取第一个 Hint 传入的分片值
        String value = shardingValue.getValues().iterator().next();

        // 返回计算出的单一库名
        return Collections.singletonList(doByValue(availableTargetNames, value));
    }

    /**
     * 通用计算逻辑：根据传入值和可用目标计算索引
     */
    private String doByValue(Collection<String> targets, String value) {
        // 核心修正：直接使用 targets 全集。
        // 这里 targets 的内容取决于 YAML 定义。
        List<String> realDbs = targets.stream().sorted().collect(Collectors.toList());

        if (realDbs.isEmpty()) {
            throw new IllegalStateException("算法未检测到任何可用的数据源目标");
        }

        int index = Math.abs(value.hashCode()) % realDbs.size();
        String target = realDbs.get(index);

        log.info("[HINT-DB] Hint强制路由值: {} -> 目标库: {}", value, target);
        return target;
    }

    @Override
    public Collection<String> doSharding(Collection<String> availableTargetNames,
                                         RangeShardingValue<String> shardingValue) {
        throw new UnsupportedOperationException("Hint 分库不支持范围匹配");
    }

    @Override
    public String getType() {
        return "CLASS_BASED";
    }

    @Override
    public void init() {
    }
}