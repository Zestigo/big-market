package com.c.domain.activity.service.quota.rule.impl;

import com.c.domain.activity.model.entity.ActivityCountEntity;
import com.c.domain.activity.model.entity.ActivityEntity;
import com.c.domain.activity.model.entity.ActivitySkuEntity;
import com.c.domain.activity.model.vo.ActivityStateVO;
import com.c.domain.activity.service.quota.rule.AbstractActionChain;
import com.c.types.enums.ResponseCode;
import com.c.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * 活动规则处理节点：基础准入校验
 * 职责：
 * 1. 校验活动运行状态（是否开启）。
 * 2. 校验活动有效期（是否在当前时间范围内）。
 * 3. 校验 SKU 基础库存（是否存在静态剩余库存）。
 *
 * @author cyh
 * @date 2026/01/29
 */
@Slf4j
@Component("activity_base_action")
public class ActivityBaseActionChain extends AbstractActionChain {

    /**
     * 执行基础校验
     *
     * @return true - 基础校验通过且链路执行完毕；抛出异常则代表校验不通过
     * @throws AppException 业务逻辑异常，用于反馈前端拦截原因
     */
    @Override
    public boolean action(ActivitySkuEntity activitySkuEntity, ActivityEntity activityEntity,
                          ActivityCountEntity activityCountEntity) {
        log.info("活动责任链-基础校验开始 [sku:{}, activityId:{}]", activitySkuEntity.getSku(), activityEntity.getActivityId());

        // 1. 校验活动状态：拦截非开启状态的活动
        if (!ActivityStateVO.OPEN.equals(activityEntity.getState())) {
            throw new AppException(ResponseCode.ACTIVITY_STATE_ERROR);
        }

        // 2. 校验活动日期：验证当前时间是否在活动有效期 [Begin, End] 内
        Date currentDate = new Date();
        if (activityEntity
                .getBeginDateTime()
                .after(currentDate) || activityEntity
                .getEndDateTime()
                .before(currentDate)) {
            throw new AppException(ResponseCode.ACTIVITY_DATE_ERROR);
        }

        // 3. 校验 SKU 静态库存：检查基础配置库存是否耗尽（作为第一层快速拦截）
        if (activitySkuEntity.getStockCountSurplus() <= 0) {
            throw new AppException(ResponseCode.ACTIVITY_SKU_STOCK_ERROR);
        }

        // 4. 推进链路：基础校验通过，流转至下一个节点（例如：库存预扣节点）
        // 增加 null 检查，防止作为链路末端节点时抛出 NPE
        return next() == null || next().action(activitySkuEntity, activityEntity, activityCountEntity);
    }
}