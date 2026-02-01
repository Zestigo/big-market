package com.c.domain.activity.service.partake;

import com.c.domain.activity.model.aggregate.CreatePartakeOrderAggregate;
import com.c.domain.activity.model.entity.ActivityEntity;
import com.c.domain.activity.model.entity.PartakeRaffleActivityEntity;
import com.c.domain.activity.model.entity.UserRaffleOrderEntity;
import com.c.domain.activity.model.vo.ActivityStateVO;
import com.c.domain.activity.repositor.IActivityRepository;
import com.c.domain.activity.service.IRaffleActivityPartakeService;
import com.c.types.enums.ResponseCode;
import com.c.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;

/**
 * 抽奖活动参与抽象类
 * 1. 流程编排：定义参与活动、扣减额度、生成订单的标准算法骨架。
 * 2. 公共校验：统一处理活动状态、有效期等与具体账户类型无关的通用逻辑。
 * 3. 事务边界：明确一个参与行为作为一个聚合根的原子性操作，交由仓储层完成持久化。
 *
 * @author cyh
 * @date 2026/02/01
 */
@Slf4j
public abstract class AbstractRaffleActivityPartake implements IRaffleActivityPartakeService {

    protected final IActivityRepository activityRepository;

    public AbstractRaffleActivityPartake(IActivityRepository activityRepository) {
        this.activityRepository = activityRepository;
    }

    /**
     * 创建抽奖参与订单（模板方法）
     * 遵循标准的活动参与流水线：查询 -> 校验 -> 幂等检查 -> 额度过滤 -> 订单构建 -> 事务持久化。
     *
     * @param partakeRaffleActivityEntity 参与抽奖活动请求实体对象
     * @return UserRaffleOrderEntity 用户抽奖订单实体
     */
    @Override
    public UserRaffleOrderEntity createOrder(PartakeRaffleActivityEntity partakeRaffleActivityEntity) {
        // 0. 基础上下文信息初始化
        String userId = partakeRaffleActivityEntity.getUserId();
        Long activityId = partakeRaffleActivityEntity.getActivityId();
        Date currentDate = new Date();

        // 1. 活动基础信息查询与前置校验（准入检查）
        ActivityEntity activityEntity = activityRepository.queryRaffleActivityByActivityId(activityId);

        // [校验] 活动状态：必须为开启 (open) 状态，防止在活动暂停或下线期间非法参与
        if (!ActivityStateVO.open.equals(activityEntity.getState())) {
            throw new AppException(ResponseCode.ACTIVITY_STATE_ERROR.getCode(),
                    ResponseCode.ACTIVITY_STATE_ERROR.getInfo());
        }

        // [校验] 活动有效期：当前时间必须在活动定义的 [开始时间, 结束时间] 闭区间内
        if (activityEntity.getBeginDateTime().after(currentDate) || activityEntity.getEndDateTime()
                                                                                  .before(currentDate)) {
            throw new AppException(ResponseCode.ACTIVITY_DATE_ERROR.getCode(),
                    ResponseCode.ACTIVITY_DATE_ERROR.getInfo());
        }

        // 2. 幂等性检查：查询用户是否存在“已创建但未消耗”的参与订单
        // 设计意图：防止网络抖动导致的重复下单导致额度超扣，实现请求重入。
        UserRaffleOrderEntity userRaffleOrderEntity =
                activityRepository.queryNoUsedRaffleOrder(partakeRaffleActivityEntity);
        if (null != userRaffleOrderEntity) {
            log.info("触发幂等逻辑：用户重复请求下单，返回已有订单。userId:{} activityId:{} orderId:{}", userId, activityId,
                    userRaffleOrderEntity.getOrderId());
            return userRaffleOrderEntity;
        }

        // 3. 账户额度过滤（抽象方法：由子类实现具体的分级账户校验逻辑，如总、月、日账户）
        // 执行此步代表用户进入了真实的“扣减额度”环节。
        CreatePartakeOrderAggregate createPartakeOrderAggregate = doFilterAccount(userId, activityId,
                currentDate);

        // 4. 构建抽奖订单（抽象方法：由子类实现，包含订单 ID 生成及快照填充）
        UserRaffleOrderEntity userRaffleOrder = buildUserRaffleOrder(userId, activityId, currentDate);

        // 5. 组装参与行为聚合根
        // 将参与所需的账户变更信息与新生成的订单信息绑定，作为领域内的一个事务单元。
        createPartakeOrderAggregate.setUserRaffleOrderEntity(userRaffleOrder);

        // 6. 持久化聚合对象
        // 核心设计：在同一个数据库事务内完成账户扣减与订单写入，保障最终一致性。
        activityRepository.saveCreatePartakeOrderAggregate(createPartakeOrderAggregate);

        // 7. 返回创建成功的订单信息
        return userRaffleOrder;
    }

    /**
     * 子类实现：执行具体的额度账户校验与过滤逻辑
     * 涉及：总账户、月账户、日账户的级联判断及额度计算。
     *
     * @param userId      用户ID
     * @param activityId  活动ID
     * @param currentDate 当前时间
     * @return 参与订单聚合根基础对象
     */
    protected abstract CreatePartakeOrderAggregate doFilterAccount(String userId, Long activityId,
                                                                   Date currentDate);

    /**
     * 子类实现：构建特定业务场景下的用户抽奖参与订单
     * 涉及：订单 ID 算法、状态设置及基础属性填充。
     *
     * @param userId      用户ID
     * @param activityId  活动ID
     * @param currentDate 当前时间
     * @return 初始化的抽奖订单实体
     */
    protected abstract UserRaffleOrderEntity buildUserRaffleOrder(String userId, Long activityId,
                                                                  Date currentDate);

}