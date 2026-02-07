package com.c.domain.activity.service.partake;

import com.c.domain.activity.model.aggregate.CreatePartakeOrderAggregate;
import com.c.domain.activity.model.entity.ActivityEntity;
import com.c.domain.activity.model.entity.PartakeRaffleActivityEntity;
import com.c.domain.activity.model.entity.UserRaffleOrderEntity;
import com.c.domain.activity.model.vo.ActivityStateVO;
import com.c.domain.activity.repository.IActivityRepository;
import com.c.domain.activity.service.IRaffleActivityPartakeService;
import com.c.types.enums.ResponseCode;
import com.c.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;

/**
 * 抽奖活动参与抽象类（领域核心服务）
 * 1. 流程编排：利用模板方法模式定义“参与活动、额度校验、扣减、下单”的标准骨架。
 * 2. 准入校验：统一处理与账户无关的全局活动规则（状态、有效期等）。
 * 3. 领域一致性：通过聚合根 (Aggregate) 将订单生成与额度变更绑定，保障仓储操作的事务原子性。
 *
 * @author cyh
 * @since 2026/02/01
 */
@Slf4j
public abstract class AbstractRaffleActivityPartake implements IRaffleActivityPartakeService {

    /** 抽奖活动仓储服务：处理领域对象的持久化及跨表查询 */
    protected final IActivityRepository activityRepository;

    public AbstractRaffleActivityPartake(IActivityRepository activityRepository) {
        this.activityRepository = activityRepository;
    }

    /**
     * 创建抽奖参与订单（便利入参方式）
     *
     * @param userId      用户唯一 ID
     * @param activityId  活动配置 ID
     * @return UserRaffleOrderEntity 用户抽奖参与凭证
     */
    @Override
    public UserRaffleOrderEntity createOrder(String userId, Long activityId) {
        return createOrder(PartakeRaffleActivityEntity.builder()
                                                      .userId(userId)
                                                      .activityId(activityId)
                                                      .build());
    }

    /**
     * 创建抽奖参与订单（核心模板方法）
     * 遵循标准的活动参与流水线：查询 -> 全局准入校验 -> 业务重入检查 -> 子类额度过滤 -> 订单封装 -> 持久化聚合。
     *
     * @param partakeRaffleActivityEntity 参与活动请求实体
     * @return UserRaffleOrderEntity 创建成功的参与凭证
     * @throws AppException 业务逻辑异常（状态错误、日期失效、额度不足等）
     */
    @Override
    public UserRaffleOrderEntity createOrder(PartakeRaffleActivityEntity partakeRaffleActivityEntity) {
        // [1] 上下文环境初始化
        String userId = partakeRaffleActivityEntity.getUserId();
        Long activityId = partakeRaffleActivityEntity.getActivityId();
        Date currentDate = new Date();

        // [2] 准入条件过滤：查询活动元数据并执行全局状态检查
        ActivityEntity activityEntity = activityRepository.queryRaffleActivityByActivityId(activityId);

        // 校验：活动状态（非开启状态不可参与）
        if (!ActivityStateVO.OPEN.equals(activityEntity.getState())) {
            throw new AppException(ResponseCode.ACTIVITY_STATE_ERROR);
        }

        // 校验：活动有效期（当前时间必须在活动生效期内）
        if (activityEntity.getBeginDateTime().after(currentDate) ||
                activityEntity.getEndDateTime().before(currentDate)) {
            throw new AppException(ResponseCode.ACTIVITY_DATE_ERROR);
        }

        // [3] 幂等性与重入性检查
        // 逻辑：查询是否存在“已申请但未实际消耗”的订单，防止因重试或并发请求导致额度被多次扣减
        UserRaffleOrderEntity userRaffleOrderEntity = activityRepository.queryNoUsedRaffleOrder(partakeRaffleActivityEntity);
        if (null != userRaffleOrderEntity) {
            log.info("触发请求重入防护：userId:{} activityId:{} 已存在未消耗订单:{}", userId, activityId, userRaffleOrderEntity.getOrderId());
            return userRaffleOrderEntity;
        }

        // [4] 账户额度差异化校验（钩子方法：具体计算逻辑由子类定义，如判断总余额/日限额等）
        // 执行至此表明已通过基础校验，准备进入账务变动流程
        CreatePartakeOrderAggregate createPartakeOrderAggregate = doFilterAccount(userId, activityId, currentDate);

        // [5] 领域订单构建
        // 封装本次参与的上下文信息，如订单号生成、关联策略 ID 镜像等
        UserRaffleOrderEntity userRaffleOrder = buildUserRaffleOrder(userId, activityId, currentDate);

        // [6] 聚合根组装与持久化
        // 确保“额度扣减”与“订单落库”作为单个事务单元处理，由仓储层适配器（Repository Adapter）保障原子性
        createPartakeOrderAggregate.setUserRaffleOrderEntity(userRaffleOrder);
        activityRepository.saveCreatePartakeOrderAggregate(createPartakeOrderAggregate);

        return userRaffleOrder;
    }

    /**
     * 子类实现：执行具体的账户额度校验与账户变动过滤
     * 核心职责：处理复杂的级联账户判断（如：判断总余额 -> 判断日/月剩余次数），并构建聚合根的基础镜像。
     *
     * @param userId      用户唯一 ID
     * @param activityId  活动配置 ID
     * @param currentDate 系统当前时间
     * @return 包含账户变动意图的聚合根对象
     */
    protected abstract CreatePartakeOrderAggregate doFilterAccount(String userId, Long activityId, Date currentDate);

    /**
     * 子类实现：构建特定业务场景下的参与订单实体
     * 核心职责：生成唯一的全局订单号，并对活动配置进行快照化，解耦后续抽奖逻辑。
     *
     * @param userId      用户唯一 ID
     * @param activityId  活动配置 ID
     * @param currentDate 系统当前时间
     * @return 初始化的用户抽奖订单实体
     */
    protected abstract UserRaffleOrderEntity buildUserRaffleOrder(String userId, Long activityId, Date currentDate);

}