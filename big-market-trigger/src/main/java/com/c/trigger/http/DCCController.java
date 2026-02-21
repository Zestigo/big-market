package com.c.trigger.http;

import com.alibaba.nacos.api.config.ConfigService;
import com.c.trigger.api.IDCCService;
import com.c.types.enums.ResponseCode;
import com.c.types.model.Response;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * DCC (Dynamic Configuration Center) 动态配置中心管理接口
 * 核心能力：
 * 1. 提供 Nacos 配置的动态更新接口，支持指定 DataID/Group 精准更新
 * 2. 兼容新旧两种模式：指定 DataID 模式（增强型）、Key 作为 DataID 模式（旧版兼容）
 * 3. 统一返回标准的 Response 格式，包含状态码、信息和操作结果
 *
 * @author cyh
 * @date 2026/02/21
 * @see IDCCService 配置管理接口定义
 * @see ConfigService Nacos 配置服务核心接口
 */
@Slf4j
@RestController
@CrossOrigin("${app.config.cross-origin}")
@RequestMapping("/api/${app.config.api-version}/raffle/dcc/")
public class DCCController implements IDCCService {

    /** Nacos 配置服务核心实例（由 Spring 容器注入） */
    @Resource
    private ConfigService configService;

    /**
     * 动态更新 Nacos 配置项
     * 核心逻辑：
     * 1. DataID 优先级：传入的 dataId 参数 > key（兼容旧逻辑，以 key 作为 DataID）
     * 2. Group 默认值：未指定时使用 DEFAULT_GROUP
     * 3. 配置发布：直接调用 Nacos API 发布配置，支持单 Key 配置和 YAML 文件配置（YAML 模式下为全量覆盖）
     * 使用示例（curl）：
     * # 增强模式（指定 DataID）
     * curl --request GET --url 'http://localhost:8091/api/v1/raffle/dcc/update_config?key=timeout&value=5000&dataId
     * =order-config.yaml&group=DEFAULT_GROUP'
     * # 兼容模式（Key 作为 DataID）
     * curl --request GET --url 'http://localhost:8091/api/v1/raffle/dcc/update_config?key=timeout&value=5000'
     *
     * @param key    配置项键名（兼容模式下作为 DataID 使用）
     * @param value  配置项值（YAML 文件模式下为全量内容，单 Key 模式下为具体值）
     * @param dataId Nacos 配置 DataID（可选，未传则使用 key 作为 DataID）
     * @param group  Nacos 配置分组（可选，默认值：DEFAULT_GROUP）
     * @return 标准响应对象：
     * - 成功：code=SUCCESS，data=true
     * - 失败：code=UN_ERROR，data=false，info 包含失败原因
     */
    @RequestMapping(value = "update_config", method = RequestMethod.GET)
    @Override
    public Response<Boolean> updateConfig(@RequestParam String key,
                                          @RequestParam String value,
                                              @RequestParam(required = false) String dataId,
                                          @RequestParam(required = false, defaultValue = "DEFAULT_GROUP") String group) {
        try {
            // 确定最终 DataID：优先使用传入的 dataId，为空则回退到 key（兼容旧逻辑）
            String finalDataId = StringUtils.isNotBlank(dataId) ? dataId : key;

            log.info("DCC 动态配置更新开始 -> DataID: {}, Group: {}, Key: {}, Value: {}", finalDataId, group, key, value);

            // 发布配置到 Nacos
            // 注意事项：
            // 1. 单 Key 模式（DataID=Key）：value 为该 Key 对应的具体值，更新仅影响该配置项
            // 2. YAML/Properties 文件模式（指定 DataID）：value 为文件全量内容，会覆盖整个文件，需谨慎使用
            boolean isPublished = configService.publishConfig(finalDataId, group, value);

            if (isPublished) {
                log.info("DCC 动态配置更新成功 -> DataID: {}", finalDataId);
                return Response
                        .<Boolean>builder()
                        .code(ResponseCode.SUCCESS.getCode())
                        .info(ResponseCode.SUCCESS.getInfo())
                        .data(true)
                        .build();
            } else {
                log.warn("DCC 动态配置更新失败 -> Nacos 写入返回 false，DataID: {}", finalDataId);
                return Response
                        .<Boolean>builder()
                        .code(ResponseCode.UN_ERROR.getCode())
                        .info("Nacos 配置发布失败，服务端返回非成功状态")
                        .data(false)
                        .build();
            }

        } catch (Exception e) {
            log.error("DCC 动态配置更新异常 -> Key: {}, DataID: {}", key, dataId, e);
            return Response
                    .<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(false)
                    .build();
        }
    }
}