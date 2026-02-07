package com.c.domain.activity.model.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 订单状态值对象
 * * 职责：描述活动参与订单的完整生命周期。
 * 作用：驱动业务流程流转，支撑支付状态同步及活动库存回滚等核心逻辑。
 * 规范：遵循常量大写、存储 Code 小写的企业级实践。
 *
 * @author cyh
 * @date 2026/01/27
 */
@Getter
@AllArgsConstructor
public enum OrderStateVO {

    /** 待支付：订单已创建，库存已预扣，等待用户完成支付动作 */
    WAIT_PAY("wait_pay", "待支付"),

    /** 已完成：支付成功，活动参与资格已确认，属于业务终态 */
    COMPLETED("completed", "已完成"),

    /** 已关闭：支付超时或手动取消，通常触发库存释放逻辑 */
    CLOSED("closed", "已关闭");

    /** 状态编码：建议存入数据库时使用小写形式 */
    private final String code;

    /** 状态描述 */
    private final String desc;

    /**
     * 静态方法：通过 Code 获取枚举对象
     * 用于在 Repository 层将数据库查出的字符串快速转换为领域枚举。
     *
     * @param code 数据库存储状态字符串
     * @return 匹配的枚举对象，未匹配返回 null
     */
    public static OrderStateVO fromCode(String code) {
        for (OrderStateVO state : OrderStateVO.values()) {
            if (state.getCode().equalsIgnoreCase(code)) {
                return state;
            }
        }
        return null;
    }

}