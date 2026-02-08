package com.c.domain.credit.model.entity;

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
 * 职责：封装上游业务触发的积分变动请求，并提供向领域聚合根转化的能力。
 *
 * @author cyh
 * @date 2026/02/08
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TradeEntity {

    /** 用户ID */
    private String userId;

    /** 交易名称 (如：行为返利、兑换抽奖) */
    private TradeNameVO tradeName;

    /** 交易类型 (forward-正向加积分、reverse-逆向扣积分) */
    private TradeTypeVO tradeType;

    /** 交易金额 */
    private BigDecimal tradeAmount;

    /** 外部业务唯一单号 (全链路幂等防重核心键) */
    private String outBusinessNo;

    /**
     * 将指令转化为领域聚合根
     * 意图：Service 层通过此方法直接获取具备业务逻辑的聚合根，无需手动拆解属性。
     *
     * @return 积分交易聚合根 {@link TradeAggregate}
     */
    public TradeAggregate toAggregate() {
        return TradeAggregate.createTradeAggregate(this);
    }

    /**
     * 校验交易指令合法性
     * 规则：关键字段非空且交易金额必须大于 0
     */
    public boolean isValid() {
        return null != tradeAmount && tradeAmount.compareTo(BigDecimal.ZERO) > 0 && StringUtils.isNotBlank(userId) && StringUtils.isNotBlank(outBusinessNo);
    }

}