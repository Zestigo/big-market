package com.c.domain.award.model.entity;

import com.c.domain.award.model.vo.TradeNameVO;
import com.c.domain.award.model.vo.TradeTypeVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 用户积分流水单实体对象
 * 职责：承载单次积分变动的核心业务信息，负责业务层的逻辑校验与聚合。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserCreditOrderEntity {

    /** 用户ID */
    private String userId;

    /** 外部业务单号（幂等键） 对应抽奖订单ID、返利记录ID等，确保来源唯一性 */
    private String outBusinessNo;

    /** 交易名称枚举（如：抽奖返利、签到积分） */
    private TradeNameVO tradeName;

    /** 交易类型枚举（正向加积分 / 反向扣积分） */
    private TradeTypeVO tradeType;

    /** 交易金额 */
    private BigDecimal amount;

    /**
     * 核心业务校验：确保金额合法性
     *
     * @return 校验结果
     */
    public boolean isAmountValid() {
        return amount != null && amount.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * 根据交易类型获取实际写入数据库的带正负号的金额
     */
    public BigDecimal getAdjustedAmount() {
        if (TradeTypeVO.REVERSE.equals(this.tradeType)) {
            return amount.negate(); // 扣减场景，金额转为负数
        }
        return amount; // 增加场景，保持正数
    }
}