package com.c.domain.credit.model.aggregate;

import com.c.domain.credit.model.entity.CreditAccountEntity;
import com.c.domain.credit.model.entity.CreditOrderEntity;
import com.c.domain.credit.model.entity.TradeEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.RandomStringUtils;

/**
 * 积分交易聚合根
 * 职责：作为积分交易的唯一入口，封装了“记账流水”与“账户变更”两个核心实体。
 * 作用：确保在领域层将交易指令（TradeEntity）转化为可执行的业务模型，保证数据在内存中的完整性。
 *
 * @author cyh
 * @date 2026/02/08
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TradeAggregate {

    /** 用户ID：作为聚合根的标识及数据库分片键 */
    private String userId;

    /** 积分账户实体：定义账户金额变动的业务意图（加/减） */
    private CreditAccountEntity creditAccountEntity;

    /** 积分订单实体：定义交易流水的详细信息及幂等单号 */
    private CreditOrderEntity creditOrderEntity;

    /**
     * 构建积分交易聚合根
     * 1. 职责：将扁平化的交易指令（TradeEntity）映射为具有层级关系的领域模型。
     * 2. 核心：在此处统一生成内部订单 ID，并将外部防重单号（outBusinessNo）下沉至流水实体。
     * 3. 优势：封装了复杂的构建逻辑，使 Service 层只需关心业务流程而非对象拼装。
     *
     * @param tradeEntity 交易指令实体，包含交易方向、金额、外部参考号等
     * @return 组装完毕的交易聚合根
     */
    public static TradeAggregate createTradeAggregate(TradeEntity tradeEntity) {
        return TradeAggregate
                .builder()
                .userId(tradeEntity.getUserId())
                // 构建流水订单实体：侧重于“记录交易事实”
                .creditOrderEntity(CreditOrderEntity
                        .builder()
                        .userId(tradeEntity.getUserId())
                        // Todo: 统一生成 12 位内部流水单号
                        .orderId(RandomStringUtils.randomNumeric(12))
                        .tradeName(tradeEntity.getTradeName())
                        .tradeType(tradeEntity.getTradeType())
                        .tradeAmount(tradeEntity.getTradeAmount())
                        .outBusinessNo(tradeEntity.getOutBusinessNo())
                        .build())
                // 构建账户变动实体：侧重于“驱动余额变更”
                .creditAccountEntity(CreditAccountEntity
                        .builder()
                        .userId(tradeEntity.getUserId())
                        .adjustAmount(tradeEntity.getTradeAmount())
                        .tradeType(tradeEntity.getTradeType())
                        .build())
                .build();
    }
}