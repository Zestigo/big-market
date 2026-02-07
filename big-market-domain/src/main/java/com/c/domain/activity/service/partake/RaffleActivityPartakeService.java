package com.c.domain.activity.service.partake;

import com.c.domain.activity.model.aggregate.CreatePartakeOrderAggregate;
import com.c.domain.activity.model.entity.ActivityAccountDayEntity;
import com.c.domain.activity.model.entity.ActivityAccountEntity;
import com.c.domain.activity.model.entity.ActivityAccountMonthEntity;
import com.c.domain.activity.model.entity.ActivityEntity;
import com.c.domain.activity.model.entity.UserRaffleOrderEntity;
import com.c.domain.activity.model.vo.UserRaffleOrderStateVO;
import com.c.domain.activity.repository.IActivityRepository;
import com.c.types.enums.ResponseCode;
import com.c.types.exception.AppException;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * 抽奖活动参与服务实现类
 * 1.  额度风控：实现“总-月-日”三级账户额度的精准校验，不存在的月/日账户自动初始化配置。
 * 2.  聚合编排：构建{@link CreatePartakeOrderAggregate}参与订单聚合根，为后续仓储层原子化持久化提供完整数据上下文。
 * 3.  订单生成：构建用户抽奖参与订单，生成临时唯一订单凭证（生产环境需替换为分布式ID）。
 *
 * @author cyh
 * @date 2026/02/01
 */
@Service
public class RaffleActivityPartakeService extends AbstractRaffleActivityPartake {

    /**
     * 时间格式化工具：自然月标识 (yyyy-MM)
     * 说明：使用{@link DateTimeFormatter}替代{@link java.text.SimpleDateFormat}，保证线程安全（Spring Service为单例）
     */
    private static final DateTimeFormatter DATE_FORMATTER_MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    /**
     * 时间格式化工具：自然日标识 (yyyy-MM-dd)
     * 替换说明：线程安全，支持多线程并发调用，无日期格式化错乱风险
     */
    private static final DateTimeFormatter DATE_FORMATTER_DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 构造函数注入仓储层依赖（Spring推荐方式，保证依赖可测试性）
     *
     * @param activityRepository 活动仓储接口，提供活动、账户相关数据查询与操作能力
     */
    public RaffleActivityPartakeService(IActivityRepository activityRepository) {
        super(activityRepository);
    }

    /**
     * 过滤并校验用户账户额度（总>月>日 三级阶梯校验）
     * 1.  总额度校验：用户活动总账户必须存在且剩余次数>0（参与行为的基础前提）。
     * 2.  时间窗口转换：将当前参与时间转换为“年月”“年月日”标识，用于月/日账户查询。
     * 3.  月额度校验与初始化：存在则校验剩余次数，不存在则从总账户拉取配置初始化月账户。
     * 4.  日额度校验与初始化：同月亮化逻辑，保证日账户可用。
     * 5.  聚合根组装：将所有账户上下文、标识信息组装为聚合根，供后续持久化使用。
     *
     * @param userId      用户唯一标识，关联用户活动账户
     * @param activityId  抽奖活动ID，关联具体活动配置
     * @param currentDate 当前参与时间（作为快照时间点，用于确定月/日时间窗口）
     * @return {@link CreatePartakeOrderAggregate} 参与订单聚合根，包含总/月/日账户信息、账户是否存在标识
     * @throws AppException 当总/月/日账户剩余额度不足时，抛出对应业务异常
     */
    @Override
    protected CreatePartakeOrderAggregate doFilterAccount(String userId, Long activityId, Date currentDate) {
        // 1. 查询并校验总账户额度（这是所有参与行为的基准，无总账户或额度不足直接拒绝）
        ActivityAccountEntity activityAccountEntity = activityRepository.queryActivityAccountByUserId(userId,
                activityId);
        if (null == activityAccountEntity || activityAccountEntity.getTotalCountSurplus() <= 0) {
            throw new AppException(ResponseCode.ACCOUNT_QUOTA_ERROR);
        }

        // 2. 转换当前时间窗口标识（将Date转换为LocalDate，适配线程安全的DateTimeFormatter）
        LocalDate currentLocalDate = currentDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        String month = DATE_FORMATTER_MONTH.format(currentLocalDate);
        String day = DATE_FORMATTER_DAY.format(currentLocalDate);

        // 3. 月账户：校验剩余额度 + 不存在则初始化
        ActivityAccountMonthEntity activityAccountMonthEntity =
                activityRepository.queryActivityAccountMonthByUserId(userId, activityId, month);

        // 3.1 月账户额度不足，抛出业务异常
        if (null != activityAccountMonthEntity && activityAccountMonthEntity.getMonthCountSurplus() <= 0) {
            throw new AppException(ResponseCode.ACCOUNT_MONTH_QUOTA_ERROR);
        }

        // 3.2 标记月账户是否存在（用于仓储层判断新增/更新），不存在则从总账户拉取配置初始化
        boolean isExistAccountMonth = null != activityAccountMonthEntity;
        if (null == activityAccountMonthEntity) {
            activityAccountMonthEntity = new ActivityAccountMonthEntity();
            activityAccountMonthEntity.setUserId(userId);
            activityAccountMonthEntity.setActivityId(activityId);
            activityAccountMonthEntity.setMonth(month);
            // 从总账户配置中拉取单月限额初始值（总账户配置为月账户的默认额度）
            activityAccountMonthEntity.setMonthCount(activityAccountEntity.getMonthCount());
            activityAccountMonthEntity.setMonthCountSurplus(activityAccountEntity.getMonthCount());
        }

        // 4. 日账户：校验剩余额度 + 不存在则初始化（逻辑同月账户，粒度更细到自然日）
        ActivityAccountDayEntity activityAccountDayEntity = activityRepository.queryActivityAccountDayByUserId(userId
                , activityId, day);

        // 4.1 日账户额度不足，抛出业务异常
        if (null != activityAccountDayEntity && activityAccountDayEntity.getDayCountSurplus() <= 0) {
            throw new AppException(ResponseCode.ACCOUNT_DAY_QUOTA_ERROR);
        }

        // 4.2 标记日账户是否存在，不存在则从总账户拉取配置初始化
        boolean isExistAccountDay = null != activityAccountDayEntity;
        if (null == activityAccountDayEntity) {
            activityAccountDayEntity = new ActivityAccountDayEntity();
            activityAccountDayEntity.setUserId(userId);
            activityAccountDayEntity.setActivityId(activityId);
            activityAccountDayEntity.setDay(day);
            // 从总账户配置中拉取单日限额初始值，作为当日账户剩余额度初始值
            activityAccountDayEntity.setDayCount(activityAccountEntity.getDayCount());
            activityAccountDayEntity.setDayCountSurplus(activityAccountEntity.getDayCount());
        }

        // 5. 组装参与订单聚合根（封装所有账户上下文，供仓储层执行原子化数据库操作（新增/更新））
        CreatePartakeOrderAggregate createPartakeOrderAggregate = new CreatePartakeOrderAggregate();
        createPartakeOrderAggregate.setUserId(userId);
        createPartakeOrderAggregate.setActivityId(activityId);
        createPartakeOrderAggregate.setActivityAccountEntity(activityAccountEntity);

        // 月维度上下文：账户实体 + 是否存在标识
        createPartakeOrderAggregate.setExistAccountMonth(isExistAccountMonth);
        createPartakeOrderAggregate.setActivityAccountMonthEntity(activityAccountMonthEntity);

        // 日维度上下文：账户实体 + 是否存在标识
        createPartakeOrderAggregate.setExistAccountDay(isExistAccountDay);
        createPartakeOrderAggregate.setActivityAccountDayEntity(activityAccountDayEntity);

        return createPartakeOrderAggregate;
    }

    /**
     * 构建用户抽奖参与订单实体（生成抽奖“入场券”，记录订单核心快照信息）
     * 核心功能：从活动元数据中拉取关键配置，生成唯一订单号，初始化订单状态。
     *
     * @param userId      用户ID，关联订单所属用户
     * @param activityId  活动ID，关联订单所属抽奖活动
     * @param currentDate 当前时间，作为订单创建时间
     * @return {@link UserRaffleOrderEntity} 用户抽奖参与订单实体，包含订单核心快照信息
     * @throws AppException 当活动元数据查询不到时，抛出对应业务异常（隐含在仓储层查询中）
     */
    @Override
    protected UserRaffleOrderEntity buildUserRaffleOrder(String userId, Long activityId, Date currentDate) {
        // 1. 查询活动元数据（包含活动名称、策略ID等关键配置，用于订单快照构建）
        ActivityEntity activityEntity = activityRepository.queryRaffleActivityByActivityId(activityId);
        if (null == activityEntity) {
            throw new AppException(ResponseCode.ACTIVITY_NOT_EXIST);
        }

        // 2. 组装订单快照（使用Builder模式简化对象构建，记录订单核心不变信息）
        return UserRaffleOrderEntity.builder().userId(userId).activityId(activityId)
                                    .activityName(activityEntity.getActivityName())
                                    .strategyId(activityEntity.getStrategyId())
                                    // 临时生成12位纯数字订单号（生产环境注意：需替换为分布式ID生成器（如雪花算法、UUID优化版）
                                    // 避免高并发下订单号重复，保证订单唯一性）
                                    .orderId(RandomStringUtils.randomNumeric(12)).orderTime(currentDate)
                                    // 订单初始状态：创建完成（等待后续抽奖动作消耗订单）
                                    .orderState(UserRaffleOrderStateVO.CREATE)
                                    .endDateTime(activityEntity.getEndDateTime()).build();
    }
}