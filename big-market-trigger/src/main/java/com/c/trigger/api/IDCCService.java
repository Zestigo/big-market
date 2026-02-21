package com.c.trigger.api;

import com.c.types.model.Response;

/**
 * 动态配置中心（DCC）服务接口。
 * 定义了配置项远程维护的标准化契约，支持多维度配置隔离。
 *
 * @author cyh
 * @date 2026/02/21
 */
public interface IDCCService {

    /**
     * 更新动态配置项（增强版）
     * 逻辑：支持指定 DataID 和 Group，实现对特定配置文件或业务组的精准打击。
     *
     * @param key    配置项的具体 Key 标识
     * @param value  需要更新的配置值
     * @param dataId 所属的 Nacos 配置文件名（可选，若为空则默认使用 key）
     * @param group  配置逻辑分组（可选，默认为 DEFAULT_GROUP）
     * @return 更新结果封装
     */
    Response<Boolean> updateConfig(String key, String value, String dataId, String group);

}