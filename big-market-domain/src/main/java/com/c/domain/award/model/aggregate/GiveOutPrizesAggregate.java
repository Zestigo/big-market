package com.c.domain.award.model.aggregate;

import com.c.domain.award.model.entity.UserAwardRecordEntity;
import com.c.domain.award.model.entity.UserCreditAwardEntity;
import com.c.domain.award.model.vo.AwardStateVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * @author cyh
 * @description 发奖聚合对象
 * @date 2026/02/07
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GiveOutPrizesAggregate {

    /** 用户ID */
    private String userId;

    /** 用户中奖记录实体 */
    private UserAwardRecordEntity userAwardRecordEntity;

    /** 用户积分奖品实体 */
    private UserCreditAwardEntity userCreditAwardEntity;

}