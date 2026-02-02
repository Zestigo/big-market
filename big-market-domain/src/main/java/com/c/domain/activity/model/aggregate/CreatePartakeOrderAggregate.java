package com.c.domain.activity.model.aggregate;

import com.c.domain.activity.model.entity.ActivityAccountDayEntity;
import com.c.domain.activity.model.entity.ActivityAccountEntity;
import com.c.domain.activity.model.entity.ActivityAccountMonthEntity;
import com.c.domain.activity.model.entity.UserRaffleOrderEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 参与抽奖活动订单聚合根
 * * 职责定位：
 * 1. 事务一致性：封装了用户参与活动时涉及的【总账-月账-日账-抽奖单】的所有变更，确保仓储层落库时的原子性。
 * 2. 状态驱动：通过 isExist 标记位引导仓储层执行“增量更新”或“初始化创建”逻辑。
 * *
 *
 * @author cyh
 * @date 2026/02/01
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CreatePartakeOrderAggregate {

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 活动ID
     */
    private Long activityId;

    /**
     * 活动账户总额度实体
     * 记录用户的总参与次数限制及其剩余次数
     */
    private ActivityAccountEntity activityAccountEntity;

    /**
     * 是否存在月账户快照
     * true: 执行 update 扣减额度；false: 执行 insert 初始化月度记录
     */
    private boolean isExistAccountMonth = true;

    /**
     * 活动账户月额度实体
     * 对应特定自然月的频次控制
     */
    private ActivityAccountMonthEntity activityAccountMonthEntity;

    /**
     * 是否存在日账户快照
     * true: 执行 update 扣减额度；false: 执行 insert 初始化当日记录
     */
    private boolean isExistAccountDay = true;

    /**
     * 活动账户日额度实体
     * 对应特定日期（yyyy-MM-dd）的频次控制
     */
    private ActivityAccountDayEntity activityAccountDayEntity;

    /**
     * 用户抽奖订单实体
     * 参与成功后生成的业务单据，作为后续执行抽奖逻辑的凭证
     */
    private UserRaffleOrderEntity userRaffleOrderEntity;

}