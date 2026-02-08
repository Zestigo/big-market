package com.c.infrastructure.dao;

import com.c.infrastructure.po.Award;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 奖品配置数据访问接口 (DAO)
 * 负责奖品基础信息（名称、类型、发放配置等）的数据库交互。
 * 该数据属于“配置型实体”，特征为高频读取、低频修改。
 *
 * @author cyh
 * @date 2026/01/29
 */
@Mapper
public interface IAwardDao {

    /**
     * 查询全量奖品配置列表
     * 提示：
     * 1. 高并发场景下，建议将此结果集缓存至 Redis 或本地内存。
     * 2. 返回的 PO 对象在领域层使用时，建议转换为 AwardEntity。
     *
     * @return 奖品配置列表；若无数据则返回空集合
     */
    List<Award> queryAwardList();

    /**
     * 根据奖品 ID 查询发奖策略配置
     *
     * @param awardId 奖品唯一 ID
     * @return 发奖配置（通常为 JSON 字符串），定义具体的发放逻辑参数
     */
    String queryAwardConfigByAwardId(Integer awardId);

    /**
     * 根据奖品 ID 查询奖品业务标识 (Key)
     *
     * @param awardId 奖品唯一 ID
     * @return 奖品业务 Key，用于对接外部库存系统或奖品核销
     */
    String queryAwardKeyByAwardId(Integer awardId);
}