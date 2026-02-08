package com.c.domain.credit.model.entity;

import com.c.domain.credit.model.vo.TradeTypeVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 积分账户实体
 * 职责：封装用户积分变动的核心信息，用于执行账户额度的调整操作。
 *
 * @author cyh
 * @date 2026/02/08
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CreditAccountEntity {

    /** 用户 ID */
    private String userId;

    /** 变动金额（设计规范：建议入参始终为正数，业务方向由 tradeType 决定） */
    private BigDecimal adjustAmount;

    /** 交易类型：决定了后续是执行“增加”还是“扣减”的数据库指令 */
    private TradeTypeVO tradeType;

    /**
     * 判断是否为正向交易（加分交易）
     * 用于 Repository 路由至 updateAddAmount 或执行 INSERT 操作
     *
     * @return true - 正向交易
     */
    public boolean isForward() {
        return TradeTypeVO.FORWARD.equals(this.tradeType);
    }

    /**
     * 判断是否为逆向交易（扣分交易）
     * 用于 Repository 路由至 updateSubtractionAmount 并触发余额足额校验
     *
     * @return true - 逆向交易
     */
    public boolean isReverse() {
        return TradeTypeVO.REVERSE.equals(this.tradeType);
    }

    /**
     * 健壮性检查：确保变动金额合法
     * 防止在聚合根组装阶段出现负数金额干扰 SQL 的加减逻辑
     */
    public boolean isAmountValid() {
        return this.adjustAmount != null && this.adjustAmount.compareTo(BigDecimal.ZERO) >= 0;
    }
}