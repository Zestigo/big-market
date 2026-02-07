package com.c.domain.award.model.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 奖品发放状态值对象
 * 1. 业务进度标识：描述中奖记录从“产生”到“到账”的生命周期。
 * 2. 幂等拦截依据：作为发奖逻辑的“状态门闩”，防止同一订单重复发奖。
 * 3. 审计对账：用于区分已发、待发及异常订单，支撑财务与运营核算。
 *
 * @author cyh
 * @date 2026/02/01
 */
@Getter
@AllArgsConstructor
public enum AwardStateVO {

    /** 待处理：中奖记录已落库，异步发奖逻辑尚未执行 */
    CREATE("create", "待发奖"),

    /** 发奖完成：奖品已成功触达用户，业务逻辑终态 */
    COMPLETED("completed", "发奖完成"),

    /** 发奖失败：发放过程触发明确异常，需人工接入或补偿 */
    FAIL("fail", "发奖失败");

    /** 状态编码（存入数据库的值） */
    private final String code;

    /** 状态描述 */
    private final String desc;

    /**
     * 静态方法：通过 Code 获取枚举对象
     * 场景：用于从数据库读取状态字符串后，将其还原为领域枚举对象。
     *
     * @param code 数据库存储的状态编码
     * @return 匹配的枚举对象，未匹配时返回 null
     */
    public static AwardStateVO fromCode(String code) {
        for (AwardStateVO state : AwardStateVO.values()) {
            if (state
                    .getCode()
                    .equalsIgnoreCase(code)) {
                return state;
            }
        }
        return null;
    }

}