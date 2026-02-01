package com.c.domain.activity.service.partake;

import com.c.domain.activity.model.aggregate.CreatePartakeOrderAggregate;
import com.c.domain.activity.model.entity.*;
import com.c.domain.activity.model.vo.UserRaffleOrderStateVO;
import com.c.domain.activity.repositor.IActivityRepository;
import com.c.types.enums.ResponseCode;
import com.c.types.exception.AppException;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 抽奖活动参与服务实现类
 * 1. 额度风控：实现“总-月-日”三级账户额度的精准校验与动态初始化。
 * 2. 聚合编排：构建活动参与所需的【参与订单聚合根】，为后续持久化提供完整的数据上下文。
 * 3. 订单生成：利用策略模式和随机算法，生成唯一抽奖参与凭证（订单 ID）。
 *
 * @author cyh
 * @date 2026/02/01
 */
@Service
public class RaffleActivityPartakeService extends AbstractRaffleActivityPartake {

    /** 时间格式化工具：自然月标识 (yyyy-MM) */
    private final SimpleDateFormat dateFormatMonth = new SimpleDateFormat("yyyy-MM");
    /** 时间格式化工具：自然日标识 (yyyy-MM-dd) */
    private final SimpleDateFormat dateFormatDay = new SimpleDateFormat("yyyy-MM-dd");

    public RaffleActivityPartakeService(IActivityRepository activityRepository) {
        super(activityRepository);
    }

    /**
     * 过滤并校验用户账户额度（总、月、日）
     * 业务逻辑：
     * 1. 【总额度校验】：检查用户活动账户是否存在且总剩余次数 > 0。
     * 2. 【时间窗口计算】：基于当前时间计算所属月、所属日。
     * 3. 【阶梯校验与初始化】：分别校验月、日额度。若当日/当月账户不存在，则从总账户模板中初始化配置。
     *
     * @param userId      用户唯一标识
     * @param activityId  抽奖活动 ID
     * @param currentDate 当前参与时间（作为快照时间点）
     * @return {@link CreatePartakeOrderAggregate} 参与订单聚合根
     */
    @Override
    protected CreatePartakeOrderAggregate doFilterAccount(String userId, Long activityId, Date currentDate) {
        // 1. 查询并校验总账户额度（这是所有参与行为的基准）
        ActivityAccountEntity activityAccountEntity =
                activityRepository.queryActivityAccountByUserId(userId, activityId);
        if (null == activityAccountEntity || activityAccountEntity.getTotalCountSurplus() <= 0) {
            throw new AppException(ResponseCode.ACCOUNT_QUOTA_ERROR.getCode(),
                    ResponseCode.ACCOUNT_QUOTA_ERROR.getInfo());
        }

        // 2. 转换当前时间窗口标识
        String month = dateFormatMonth.format(currentDate);
        String day = dateFormatDay.format(currentDate);

        // 3. 校验月账户额度
        ActivityAccountMonthEntity activityAccountMonthEntity =
                activityRepository.queryActivityAccountMonthByUserId(userId, activityId, month);
        if (null != activityAccountMonthEntity && activityAccountMonthEntity.getMonthCountSurplus() <= 0) {
            throw new AppException(ResponseCode.ACCOUNT_MONTH_QUOTA_ERROR.getCode(),
                    ResponseCode.ACCOUNT_MONTH_QUOTA_ERROR.getInfo());
        }

        // 3.1 标记并初始化月账户（若不存在则准备在持久化阶段进行新增）
        boolean isExistAccountMonth = null != activityAccountMonthEntity;
        if (null == activityAccountMonthEntity) {
            activityAccountMonthEntity = new ActivityAccountMonthEntity();
            activityAccountMonthEntity.setUserId(userId);
            activityAccountMonthEntity.setActivityId(activityId);
            activityAccountMonthEntity.setMonth(month);
            // 从总账户配置中拉取单月限额初始值
            activityAccountMonthEntity.setMonthCount(activityAccountEntity.getMonthCount());
            activityAccountMonthEntity.setMonthCountSurplus(activityAccountEntity.getMonthCountSurplus());
        }

        // 4. 校验日账户额度
        ActivityAccountDayEntity activityAccountDayEntity =
                activityRepository.queryActivityAccountDayByUserId(userId, activityId, day);
        if (null != activityAccountDayEntity && activityAccountDayEntity.getDayCountSurplus() <= 0) {
            throw new AppException(ResponseCode.ACCOUNT_DAY_QUOTA_ERROR.getCode(),
                    ResponseCode.ACCOUNT_DAY_QUOTA_ERROR.getInfo());
        }

        // 4.1 标记并初始化日账户
        boolean isExistAccountDay = null != activityAccountDayEntity;
        if (null == activityAccountDayEntity) {
            activityAccountDayEntity = new ActivityAccountDayEntity();
            activityAccountDayEntity.setUserId(userId);
            activityAccountDayEntity.setActivityId(activityId);
            activityAccountDayEntity.setDay(day);
            // 从总账户配置中拉取单日限额初始值
            activityAccountDayEntity.setDayCount(activityAccountEntity.getDayCount());
            activityAccountDayEntity.setDayCountSurplus(activityAccountEntity.getDayCountSurplus());
        }

        // 5. 组装参与订单聚合根（用于后续仓储层执行原子数据库操作）
        CreatePartakeOrderAggregate createPartakeOrderAggregate = new CreatePartakeOrderAggregate();
        createPartakeOrderAggregate.setUserId(userId);
        createPartakeOrderAggregate.setActivityId(activityId);
        createPartakeOrderAggregate.setActivityAccountEntity(activityAccountEntity);
        // 月维度上下文
        createPartakeOrderAggregate.setExistAccountMonth(isExistAccountMonth);
        createPartakeOrderAggregate.setActivityAccountMonthEntity(activityAccountMonthEntity);
        // 日维度上下文
        createPartakeOrderAggregate.setExistAccountDay(isExistAccountDay);
        createPartakeOrderAggregate.setActivityAccountDayEntity(activityAccountDayEntity);

        return createPartakeOrderAggregate;
    }

    /**
     * 构建用户抽奖参与订单实体
     * 意图：根据活动配置快照生成本次抽奖的“入场券”。
     *
     * @param userId      用户ID
     * @param activityId  活动ID
     * @param currentDate 当前时间（下单时间）
     * @return {@link UserRaffleOrderEntity} 用户参与订单实体
     */
    @Override
    protected UserRaffleOrderEntity buildUserRaffleOrder(String userId, Long activityId, Date currentDate) {
        // 获取活动元数据（包含策略 ID、活动名称等关键信息）
        ActivityEntity activityEntity = activityRepository.queryRaffleActivityByActivityId(activityId);

        // 组装订单快照
        UserRaffleOrderEntity userRaffleOrder = new UserRaffleOrderEntity();
        userRaffleOrder.setUserId(userId);
        userRaffleOrder.setActivityId(activityId);
        userRaffleOrder.setActivityName(activityEntity.getActivityName());
        userRaffleOrder.setStrategyId(activityEntity.getStrategyId());
        // 生成 12 位纯数字随机订单号（业务建议：实际生产应使用分布式 ID 生成器如雪花算法）
        userRaffleOrder.setOrderId(RandomStringUtils.randomNumeric(12));
        userRaffleOrder.setOrderTime(currentDate);
        // 设置初始状态为“创建完成”，等待抽奖动作消耗
        userRaffleOrder.setOrderState(UserRaffleOrderStateVO.create);

        return userRaffleOrder;
    }
}