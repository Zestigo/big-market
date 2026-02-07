package com.c.domain.rebate.model.entity;

import com.c.domain.rebate.model.vo.BehaviorTypeVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 行为实体（用户触发的原始行为数据）
 *
 * @author cyh
 * @date 2026/02/05
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BehaviorEntity {

    /** 用户唯一标识 ID */
    private String userId;

    /** 行为类型枚举（包含：签到、支付等类型） */
    private BehaviorTypeVO behaviorTypeVO;

    /** 业务防重外部单号（如：签到日期 yyyyMMdd 或 外部支付流水号） */
    private String outBusinessNo;

}