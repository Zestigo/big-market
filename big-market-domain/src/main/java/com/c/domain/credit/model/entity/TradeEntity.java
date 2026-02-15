package com.c.domain.credit.model.entity;

import com.c.domain.credit.event.CreditAdjustSuccessMessageEvent;
import com.c.domain.credit.model.aggregate.TradeAggregate;
import com.c.domain.credit.model.vo.TradeNameVO;
import com.c.domain.credit.model.vo.TradeTypeVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;

/**
 * 积分交易指令实体
 *
 * @author cyh
 * @date 2026/02/09
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TradeEntity {

    /* 用户ID */
    private String userId;

    /* 交易名称 */
    private TradeNameVO tradeName;

    /* 交易类型 */
    private TradeTypeVO tradeType;

    /* 交易金额 */
    private BigDecimal tradeAmount;

    /* 外部业务唯一单号 */
    private String outBusinessNo;

    /**
     * 将指令转化为领域聚合根
     * 职责：作为入口，仅负责聚合根的初始实例化
     */
    public TradeAggregate toAggregate() {
        return TradeAggregate.createTradeAggregate(this);
    }

    /**
     * 校验交易指令合法性
     */
    public boolean isValid() {
        return null != tradeAmount && tradeAmount.compareTo(BigDecimal.ZERO) > 0 && StringUtils.isNotBlank(userId) && StringUtils.isNotBlank(outBusinessNo);
    }

}