package com.c.infrastructure.dao;

import com.c.infrastructure.po.Award;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 奖品配置数据访问接口
 * 职责：
 * 负责从数据库中检索奖品的基础信息（如奖品名称、奖品类型、发奖配置等）。
 * 该数据通常属于高频读取、低频修改的“配置型实体”。
 *
 * @author cyh
 * @date 2026/01/29
 */
@Mapper
public interface IAwardDao {

    /**
     * 查询全量奖品列表
     * <p>
     * 注意事项：
     * 1. 在高并发抽奖场景下，建议将此结果集缓存至 Redis 或本地内存中。
     * 2. 返回的 {@link Award} 是 PO 对象，在领域层使用时应转换为 AwardEntity。
     *
     * @return 奖品配置列表；若无数据则返回空集合
     */
    List<Award> queryAwardList();

}