package com.c.trigger.http;

import com.alibaba.fastjson.JSON;
import com.c.api.IRaffleActivityService;
import com.c.api.dto.*;
import com.c.domain.activity.model.entity.ActivityAccountEntity;
import com.c.domain.activity.model.entity.UserRaffleOrderEntity;
import com.c.domain.activity.service.IRaffleActivityAccountQuotaService;
import com.c.domain.activity.service.IRaffleActivityPartakeService;
import com.c.domain.activity.service.armory.IActivityArmory;
import com.c.domain.award.model.entity.UserAwardRecordEntity;
import com.c.domain.award.model.vo.AwardStateVO;
import com.c.domain.award.service.IAwardService;
import com.c.domain.rebate.model.entity.BehaviorEntity;
import com.c.domain.rebate.model.entity.BehaviorRebateOrderEntity;
import com.c.domain.rebate.model.vo.BehaviorTypeVO;
import com.c.domain.rebate.service.IBehaviorRebateService;
import com.c.domain.strategy.model.entity.RaffleAwardEntity;
import com.c.domain.strategy.model.entity.RaffleFactorEntity;
import com.c.domain.strategy.service.IRaffleStrategy;
import com.c.domain.strategy.service.armory.IStrategyArmory;
import com.c.types.enums.ResponseCode;
import com.c.types.exception.AppException;
import com.c.types.model.Response;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;

/**
 * 抽奖活动前端控制器
 * 描述：负责抽奖活动的生命周期管理，包括活动装配预热和用户参与抽奖的核心流程。
 * * @author cyh
 *
 * @since 2026/01/23
 */
@Slf4j
@RestController
@CrossOrigin("${app.config.cross-origin}")
@RequestMapping("/api/${app.config.api-version}/raffle/activity/")
public class RaffleActivityController implements IRaffleActivityService {

    @Resource
    private IRaffleActivityPartakeService raffleActivityPartakeService;
    @Resource
    private IRaffleStrategy raffleStrategy;
    @Resource
    private IAwardService awardService;
    @Resource
    private IActivityArmory activityArmory;
    @Resource
    private IStrategyArmory strategyArmory;
    @Resource
    private IBehaviorRebateService behaviorRebateService;
    @Resource
    private IRaffleActivityAccountQuotaService raffleActivityAccountQuotaService;

    private static final DateTimeFormatter DATE_FORMAT_DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * 活动装配预热
     * 职责：预热活动SKU库存缓存及抽奖策略概率矩阵，确保高并发下的响应速度。
     *
     * @param activityId 活动ID
     * @return 装配结果
     */
    @GetMapping("armory")
    public Response<Boolean> armory(@RequestParam Long activityId) {
        try {
            log.info("活动装配预热开始，activityId:{}", activityId);

            // 1. 装配活动 SKU 及其关联的库存信息 (Redis 预热)
            boolean isSkuAssembled = activityArmory.assembleActivitySkuByActivityId(activityId);
            if (!isSkuAssembled) {
                log.warn("活动 SKU 装配失败，activityId:{}", activityId);
                return Response.fail(ResponseCode.ACTIVITY_NOT_EXIST);
            }

            // 2. 装配抽奖策略及其对应的概率权重矩阵 (策略军械库预热)
            boolean isStrategyAssembled = strategyArmory.assembleLotteryStrategyByActivityId(activityId);
            if (!isStrategyAssembled) {
                log.warn("抽奖策略装配失败，activityId:{}", activityId);
                return Response.fail(ResponseCode.UN_ASSEMBLED_STRATEGY_ARMORY);
            }

            log.info("活动装配预热完成成功，activityId:{}", activityId);
            return Response.success(true);

        } catch (AppException e) {
            log.error("活动装配预热业务异常，activityId:{}", activityId, e);
            return Response.fail(e.getCode(), e.getInfo());
        } catch (Exception e) {
            log.error("活动装配预热系统异常，activityId:{}", activityId, e);
            return Response.fail(ResponseCode.UN_ERROR);
        }
    }

    /**
     * 执行用户抽奖接口
     * 流程：参数校验 -> 参与活动(下单) -> 执行策略抽奖 -> 记录中奖结果 -> 返回结果。
     *
     * @param request 抽奖请求 DTO，包含用户 ID 和活动 ID
     * @return Response<ActivityDrawResponseDTO> 中奖结果（奖品 ID、标题、排序等）
     */
    @Override
    @PostMapping("draw")
    public Response<ActivityDrawResponseDTO> draw(@RequestBody ActivityDrawRequestDTO request) {
        // 1. 定义局部变量，用于安全日志记录
        String userId = "unknown";
        Long activityId = null;

        try {
            // 2. 严谨的入参校验：先判定 request 对象本身，再判定内部属性
            if (null == request || StringUtils.isBlank(request.getUserId()) || null == request.getActivityId()) {
                log.error("抽奖请求参数非法: {}", request);
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER);
            }

            // 3. 赋值（此时已确保 request 不为 null）
            userId = request.getUserId();
            activityId = request.getActivityId();
            log.info("抽奖行为触发，userId:{} activityId:{}", userId, activityId);

            // 4. 参与活动：扣减额度并创建用户抽奖参与订单
            UserRaffleOrderEntity orderEntity = raffleActivityPartakeService.createOrder(userId, activityId);
            log.info("用户参与活动成功，userId:{} activityId:{} orderId:{}", userId, activityId, orderEntity.getOrderId());

            // 5. 执行抽奖决策
            RaffleAwardEntity raffleAwardEntity = raffleStrategy.performRaffle(RaffleFactorEntity
                    .builder()
                    .userId(userId)
                    .strategyId(orderEntity.getStrategyId())
                    .endDateTime(orderEntity.getEndDateTime())
                    .build());

            // 6. 结果持久化
            UserAwardRecordEntity userAwardRecord = UserAwardRecordEntity
                    .builder()
                    .userId(userId)
                    .activityId(activityId)
                    .strategyId(orderEntity.getStrategyId())
                    .orderId(orderEntity.getOrderId())
                    .awardId(raffleAwardEntity.getAwardId())
                    .awardConfig(raffleAwardEntity.getAwardConfig())
                    .awardTitle(raffleAwardEntity.getAwardTitle())
                    .awardTime(new Date())
                    .awardState(AwardStateVO.CREATE)
                    .build();
            awardService.saveUserAwardRecord(userAwardRecord);

            // 7. 组装响应数据
            ActivityDrawResponseDTO responseDTO = ActivityDrawResponseDTO
                    .builder()
                    .awardId(raffleAwardEntity.getAwardId())
                    .awardTitle(raffleAwardEntity.getAwardTitle())
                    .awardIndex(raffleAwardEntity.getSort())
                    .build();

            log.info("抽奖执行完成，userId:{} orderId:{} awardId:{}", userId, orderEntity.getOrderId(),
                    raffleAwardEntity.getAwardId());
            return Response.success(responseDTO);

        } catch (AppException e) {
            // 【安全修正】使用局部变量 userId 和 activityId，避免依赖可能为 null 的 request 对象
            log.error("抽奖业务异常，userId:{} activityId:{} code:{} info:{}", userId, activityId, e.getCode(), e.getInfo());
            return Response.fail(e.getCode(), e.getInfo());
        } catch (Exception e) {
            // 【安全修正】同上
            log.error("抽奖系统未知错误，userId:{} activityId:{}", userId, activityId, e);
            return Response.fail(ResponseCode.UN_ERROR);
        }
    }

    @Override
    @PostMapping("calendar_sign_rebate")
    public Response<Boolean> calendarSignRebate(@RequestParam String userId) {
        try {
            log.info("日历签到返利开始 userId:{}", userId);

            // 1. 构建行为领域对象
            BehaviorEntity behaviorEntity = BehaviorEntity
                    .builder()
                    .userId(userId)
                    .behaviorTypeVO(BehaviorTypeVO.SIGN)
                    .outBusinessNo(DATE_FORMAT_DAY.format(LocalDate.now()))
                    .build();

            // 2. 执行返利并记录单号
            List<String> orderIds = behaviorRebateService.createOrder(behaviorEntity);
            log.info("日历签到返利完成 userId:{} orderIds:{}", userId, JSON.toJSONString(orderIds));

            // 3. 返回成功结果
            return Response
                    .<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(true)
                    .build();

        } catch (AppException e) {
            log.error("日历签到返利异常 userId:{} code:{} info:{}", userId, e.getCode(), e.getInfo());
            return Response
                    .<Boolean>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("日历签到返利失败 userId:{}", userId, e); // 记得要把堆栈 e 传进去，方便排查
            return Response
                    .<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(false)
                    .build();
        }
    }

    /**
     * 查询用户今日是否已完成日历签到返利
     * 通过校验当日外部业务单号是否存在对应的返利流水，判断用户是否已领取过签到奖励。
     *
     * @param userId 用户唯一ID
     * @return Response<Boolean> true-已完成签到，false-未完成签到
     */
    @Override
    @PostMapping("is_calendar_sign_rebate")
    public Response<Boolean> isCalendarSignRebate(@RequestParam String userId) {
        try {
            log.info("查询用户是否完成日历签到返利开始 userId:{}", userId);
            // 生成当日业务防重 ID（yyyyMMdd）
            String outBusinessNo = DATE_FORMAT_DAY.format(LocalDate.now());
            // 查询对应的返利订单流水
            List<BehaviorRebateOrderEntity> behaviorRebateOrderEntities =
                    behaviorRebateService.queryOrderByOutBusinessNo(userId, outBusinessNo);

            log.info("查询用户是否完成日历签到返利完成 userId:{} orders.size:{}", userId, behaviorRebateOrderEntities.size());

            return Response
                    .<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(!behaviorRebateOrderEntities.isEmpty()) // 集合不为空则代表今日已签到
                    .build();
        } catch (Exception e) {
            log.error("查询用户是否完成日历签到返利失败 userId:{}", userId, e);
            return Response
                    .<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(false)
                    .build();
        }
    }

    /**
     * 查询用户活动账户额度
     * 获取用户在指定活动下的总次数、日次数、月次数及对应的剩余额度。
     *
     * @param request 查询请求对象（包含 userId 和 activityId）
     * @return Response<UserActivityAccountResponseDTO> 账户额度详情
     */
    @Override
    @PostMapping("query_user_activity_account")
    public Response<UserActivityAccountResponseDTO> queryUserActivityAccount(@RequestBody UserActivityAccountRequestDTO request) {
        Long activityId = request.getActivityId();
        String userId = request.getUserId();
        try {
            log.info("查询用户活动账户开始 userId:{} activityId:{}", userId, activityId);

            // 1. 参数校验：提前检查核心参数，避免无效查询
            if (StringUtils.isBlank(userId) || null == activityId) {
                return Response
                        .<UserActivityAccountResponseDTO>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info(ResponseCode.ILLEGAL_PARAMETER.getInfo())
                        .build();
            }

            // 2. 查询领域实体
            ActivityAccountEntity activityAccountEntity =
                    raffleActivityAccountQuotaService.queryActivityAccountEntity(activityId, userId);

            // 3. 对象转换：将领域实体转换为响应 DTO
            UserActivityAccountResponseDTO userActivityAccountResponseDTO = UserActivityAccountResponseDTO
                    .builder()
                    .totalCount(activityAccountEntity.getTotalCount())
                    .totalCountSurplus(activityAccountEntity.getTotalCountSurplus())
                    .dayCount(activityAccountEntity.getDayCount())
                    .dayCountSurplus(activityAccountEntity.getDayCountSurplus())
                    .monthCount(activityAccountEntity.getMonthCount())
                    .monthCountSurplus(activityAccountEntity.getMonthCountSurplus())
                    .build();

            log.info("查询用户活动账户完成 userId:{} activityId:{} dto:{}", userId, activityId,
                    JSON.toJSONString(userActivityAccountResponseDTO));

            return Response
                    .<UserActivityAccountResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(userActivityAccountResponseDTO)
                    .build();

        } catch (AppException e) {
            log.error("查询用户活动账户异常 userId:{} activityId:{} code:{} info:{}", userId, activityId, e.getCode(),
                    e.getInfo());
            return Response
                    .<UserActivityAccountResponseDTO>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("查询用户活动账户失败 userId:{} activityId:{}", userId, activityId, e);
            return Response
                    .<UserActivityAccountResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }
}