package com.c.domain.award.model.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 奖品发放状态值对象
 * 1. 业务进度标识：描述用户中奖记录从“产生”到“实际奖品到账”的完整生命周期。
 * 2. 对账依据：用于财务或运营人员审计奖品发放情况，区分已到账与待处理订单。
 * 3. 幂等拦截：作为业务执行的“状态机门闩”，防止同一笔中奖订单被重复触发发奖逻辑。
 *
 * @author cyh
 * @date 2026/02/01
 */
@Getter
@AllArgsConstructor
public enum AwardStateVO {

    /**
     * 待处理/已创建：中奖记录已持久化，但奖品（如积分、余额、实物券）尚未通过异步链路送达用户手中。
     */
    create("create", "待发奖"),

    /**
     * 已发奖：奖品发放逻辑（由 MQ 消费者驱动）已成功执行，用户已获得对应的奖励。
     * 备注：此状态是最终状态，后续重复的消息将在此被拦截，保障业务幂等。
     */
    complete("complete", "发奖完成"),

    /**
     * 发奖失败：奖品发放过程中出现非预期异常（如三方接口超时、余额不足等）。
     * 备注：该状态通常作为异常监控的红点，需要人工接入或通过特定补偿脚本修复。
     */
    fail("fail", "发奖失败"),
    ;

    /** 状态编码 */
    private final String code;

    /** 状态描述 */
    private final String desc;

}