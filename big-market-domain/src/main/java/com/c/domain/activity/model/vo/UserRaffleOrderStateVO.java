package com.c.domain.activity.model.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 值对象：用户抽奖订单状态枚举
 * * 职责：定义用户参与活动过程中，抽奖单据（参与凭证）的生命周期状态。
 * 作用：用于控制单据的可使用性，防止重复参与或非法操作。
 *
 * @author cyh
 * @date 2026/02/06
 */
@Getter
@AllArgsConstructor
public enum UserRaffleOrderStateVO {

    /**
     * 创建 (CREATE)
     * 场景：用户通过扣减个人活动额度成功下单，获得一次抽奖机会。
     * 含义：单据已生成，处于待使用状态。
     */
    CREATE("create", "创建"),

    /**
     * 已使用 (USED)
     * 场景：用户调用抽奖接口并完成了实际的规则运算。
     * 含义：单据生命周期结束，不可再次触发抽奖。
     */
    USED("used", "已使用"),

    /**
     * 已作废 (CANCEL)
     * 场景：活动过期、人工干预或订单异常冲正。
     * 含义：单据失效，额度不再返还。
     */
    CANCEL("cancel", "已作废");

    /** 状态编码 */
    private final String code;

    /** 状态描述 */
    private final String desc;

}