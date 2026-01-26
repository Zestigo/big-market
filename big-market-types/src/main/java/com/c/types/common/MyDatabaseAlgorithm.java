package com.c.types.common;

import lombok.extern.slf4j.Slf4j;
import org.apache.shardingsphere.sharding.api.sharding.standard.PreciseShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.RangeShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.StandardShardingAlgorithm;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author cyh
 * @description 自定义标准分库算法
 * 场景：用于处理 = 和 IN 的 SQL 语句。根据 user_id 进行哈希取模，决定数据落入哪个数据库。
 * @date 2026/01/26
 */
@Slf4j
public class MyDatabaseAlgorithm implements StandardShardingAlgorithm<String> {

    /**
     * 精确分片配置：在执行 INSERT 或 WHERE user_id = 'xxx' 时调用
     * * @param availableTargetNames YAML 配置中所有可用的数据源名称集合（如 [ds_0, ds_1, big_market]）
     *
     * @param shardingValue 包含逻辑表名、分片列名（user_id）以及本次 SQL 传入的具体值
     * @return 最终确定的物理数据库名称
     */
    @Override
    public String doSharding(Collection<String> availableTargetNames,
                             PreciseShardingValue<String> shardingValue) {
        System.out.println(availableTargetNames);
        // 1. 获取所有候选库并排序。排序是为了保证 index 对应的数据源在多次调用中顺序一致。
        List<String> dbs = availableTargetNames.stream().sorted().collect(Collectors.toList());

        // 2. 使用 user_id 的 hashCode 进行绝对值取模。
        // Math.abs 是为了防止 hashCode 为负数导致计算出的索引为负。
        int index = Math.abs(shardingValue.getValue().hashCode()) % dbs.size();

        // 3. 动态获取目标库。这种写法避免了硬编码 "ds_"，即使未来增加库数量也能自动适配。
        String target = dbs.get(index);

        log.info("[DB-SHARD] 逻辑表: {}, 分片列值(user_id): {} -> 目标库: {}", shardingValue.getLogicTableName(),
                shardingValue.getValue(), target);
        return target;
    }

    /**
     * 范围分片配置：用于 BETWEEN、>、< 等 SQL 场景
     * 本项目暂不支持 user_id 范围查询分库，故抛出异常
     */
    @Override
    public Collection<String> doSharding(Collection<String> availableTargetNames,
                                         RangeShardingValue<String> rangeShardingValue) {
        throw new UnsupportedOperationException("不支持 user_id 的范围查询（Range）分库");
    }

    /**
     * 算法类型标识：需与 YAML 中的 type: CLASS_BASED 匹配
     */
    @Override
    public String getType() {
        return "CLASS_BASED";
    }

    /**
     * 初始化方法：在算法加载时调用，可用于读取 props 中的自定义配置
     */
    @Override
    public void init() {
    }
}