package com.c.domain.activity.model.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 活动状态值对象
 * 职责：描述营销活动从策划、发布到结束的完整生命周期。
 * 作用：控制活动在 C 端站点的可见性、报名权限，以及在 B 端后台的操作权限。
 * 规范：常量采用大写，数据库存储编码（Code）采用小写。
 *
 * @author cyh
 * @date 2026/01/27
 */
@Getter
@AllArgsConstructor
public enum ActivityStateVO {

    /** 初始创建：活动已在后台录入，处于草稿或待审核状态，C 端不可见 */
    CREATE("create", "创建"),

    /** 已开启：活动已通过审核并上线，用户可以正常参与或进行抽奖 */
    OPEN("open", "开启"),

    /** 已关闭：活动手动下线或由于周期结束自动停用，停止一切参与逻辑 */
    CLOSE("close", "关闭");

    /** 状态编码：对应数据库中的存储值 */
    private final String code;

    /** 状态描述 */
    private final String desc;

    /**
     * 静态方法：通过 Code 获取枚举对象
     * 场景：常用于从活动表查询记录后，将状态字符串还原为领域枚举。
     *
     * @param code 数据库存储状态编码
     * @return 匹配的枚举对象，未匹配返回 null
     */
    public static ActivityStateVO fromCode(String code) {
        for (ActivityStateVO state : ActivityStateVO.values()) {
            if (state
                    .getCode()
                    .equalsIgnoreCase(code)) {
                return state;
            }
        }
        return null;
    }

}