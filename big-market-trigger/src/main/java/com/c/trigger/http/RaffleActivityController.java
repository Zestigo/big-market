package com.c.trigger.http;

import com.alibaba.fastjson.JSON;
import com.c.api.IRaffleActivityService;
import com.c.api.dto.*;
import com.c.domain.activity.model.entity.*;
import com.c.domain.activity.model.vo.OrderTradeTypeVO;
import com.c.domain.activity.service.IRaffleActivityAccountQuotaService;
import com.c.domain.activity.service.IRaffleActivityPartakeService;
import com.c.domain.activity.service.IRaffleActivitySkuProductService;
import com.c.domain.activity.service.armory.IActivityArmory;
import com.c.domain.award.model.entity.UserAwardRecordEntity;
import com.c.domain.award.model.vo.AwardStateVO;
import com.c.domain.award.service.IAwardService;
import com.c.domain.credit.model.entity.CreditAccountEntity;
import com.c.domain.credit.model.entity.TradeEntity;
import com.c.domain.credit.model.vo.TradeNameVO;
import com.c.domain.credit.model.vo.TradeTypeVO;
import com.c.domain.credit.service.ICreditAdjustService;
import com.c.domain.rebate.model.entity.BehaviorEntity;
import com.c.domain.rebate.model.entity.BehaviorRebateOrderEntity;
import com.c.domain.rebate.model.vo.BehaviorTypeVO;
import com.c.domain.rebate.service.IBehaviorRebateService;
import com.c.domain.strategy.model.entity.RaffleAwardEntity;
import com.c.domain.strategy.model.entity.RaffleFactorEntity;
import com.c.domain.strategy.service.IRaffleStrategy;
import com.c.domain.strategy.service.armory.IStrategyArmory;
import com.c.types.annotations.DCCConfiguration;
import com.c.types.annotations.DCCValue;
import com.c.types.enums.ResponseCode;
import com.c.types.exception.AppException;
import com.c.types.model.Response;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 抽奖活动前端控制器
 * 描述：负责抽奖活动的生命周期管理，包括活动装配预热和用户参与抽奖的核心流程。
 *
 * @author cyh
 * @date 2026/02/21
 */
@Slf4j
@RestController
@CrossOrigin("${app.config.cross-origin}")
@RequestMapping("/api/${app.config.api-version}/raffle/activity/")
@DCCConfiguration(prefix = "raffle.activity", dataId = "raffle-config.yaml")
@DubboService(version = "1.0")
public class RaffleActivityController implements IRaffleActivityService {

    @Resource
    private IRaffleActivityPartakeService raffleActivityPartakeService;
    @Resource
    private IRaffleActivitySkuProductService raffleActivitySkuProductService;
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
    @Resource
    private ICreditAdjustService creditAdjustService;


    /*  动态配置中心：抽奖接口降级开关
     * 最终 Key：raffle.activity.degradeSwitch
     * 所属 DataID：raffle-config.yaml
     */
    @DCCValue("degradeSwitch:open")
    private String degradeSwitch; /* 抽奖业务降级开关状态 */

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
        /* 1. 定义局部变量，用于安全日志记录及异常处理反馈 */
        String userId = "unknown";
        Long activityId = null;

        try {
            // 2. 严谨的入参校验：判定 request 对象及内部属性合法性
            if (null == request || StringUtils.isBlank(request.getUserId()) || null == request.getActivityId()) {
                log.error("抽奖请求参数非法: {}", request);
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER);
            }

            // 3. 变量赋值（确保后续 catch 块能捕获到具体用户信息）
            userId = request.getUserId();
            activityId = request.getActivityId();

            // 4. 【核心设置点】Nacos 动态降级判定
            // 逻辑：放在此处可确保日志记录是谁触发了降级，且在执行重业务（DB操作）前进行拦截
            if (!"open".equals(degradeSwitch)) {
                log.warn("抽奖接口已触发 Nacos 动态降级拦截，userId:{} activityId:{}", userId, activityId);
                return Response.fail(ResponseCode.DEGRADE_SWITCH);
            }

            log.info("抽奖行为触发，userId:{} activityId:{}", userId, activityId);

            // 5. 参与活动：扣减额度并创建用户抽奖参与记录订单
            UserRaffleOrderEntity orderEntity = raffleActivityPartakeService.createOrder(userId, activityId);
            log.info("用户参与活动成功，userId:{} activityId:{} orderId:{}", userId, activityId, orderEntity.getOrderId());

            // 6. 执行抽奖决策：基于策略 ID 和用户上下文计算中奖结果
            RaffleAwardEntity raffleAwardEntity = raffleStrategy.performRaffle(RaffleFactorEntity
                    .builder()
                    .userId(userId)
                    .strategyId(orderEntity.getStrategyId())
                    .endDateTime(orderEntity.getEndDateTime())
                    .build());

            // 7. 结果持久化：写入用户中奖记录，等待后续发奖
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

            // 8. 组装响应数据 DTO
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
            // 业务异常捕获：记录错误码和错误描述
            log.error("抽奖业务异常，userId:{} activityId:{} code:{} info:{}", userId, activityId, e.getCode(), e.getInfo());
            return Response.fail(e.getCode(), e.getInfo());
        } catch (Exception e) {
            // 系统未知异常捕获：保证接口不直接崩溃，返回通用错误
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

    /**
     * 查询 SKU 商品列表集合
     *
     * @param activityId 活动唯一标识 ID
     * @return SKU 商品列表响应对象
     */
    @Override
    @PostMapping("query_sku_product_list_by_activity_id")
    public Response<List<SkuProductResponseDTO>> querySkuProductListByActivityId(Long activityId) {
        try {
            log.info("查询 sku 商品集合开始 activityId:{}", activityId);

            // 1. 基础参数校验
            if (activityId == null) {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(),
                        ResponseCode.ILLEGAL_PARAMETER.getInfo());
            }

            // 2. 查询商品领域实体列表
            List<SkuProductEntity> skuProductEntities =
                    raffleActivitySkuProductService.querySkuProductEntityListByActivityId(activityId);

            List<SkuProductResponseDTO> skuProductResponseDTOS = new ArrayList<>(skuProductEntities.size());

            // 3. 循环进行对象转换：Entity -> DTO
            for (SkuProductEntity skuProductEntity : skuProductEntities) {

                // 转换次数配置对象
                SkuProductEntity.ActivityCount activityCountEntity = skuProductEntity.getActivityCount();
                SkuProductResponseDTO.ActivityCount activityCountDto = SkuProductResponseDTO.ActivityCount
                        .builder()
                        .totalCount(activityCountEntity.getTotalCount())
                        .dayCount(activityCountEntity.getDayCount())
                        .monthCount(activityCountEntity.getMonthCount())
                        .build();

                // 组装商品响应 DTO
                SkuProductResponseDTO skuProductResponseDTO = SkuProductResponseDTO
                        .builder()
                        .sku(skuProductEntity.getSku())
                        .activityId(skuProductEntity.getSku())
                        .activityCountId(skuProductEntity.getActivityCountId())
                        .stockCount(skuProductEntity.getStockCount())
                        .stockCountSurplus(skuProductEntity.getStockCountSurplus())
                        .productAmount(skuProductEntity.getProductAmount())
                        .activityCount(activityCountDto)
                        .build();

                skuProductResponseDTOS.add(skuProductResponseDTO);
            }

            log.info("查询 sku 商品集合完成 activityId:{} size:{}", activityId, skuProductResponseDTOS.size());

            // 4. 返回成功响应结果
            return Response
                    .<List<SkuProductResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(skuProductResponseDTOS)
                    .build();

        } catch (Exception e) {
            log.error("查询 sku 商品集合失败 activityId:{}", activityId, e);
            return Response
                    .<List<SkuProductResponseDTO>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    /**
     * 查询用户积分账户余额
     *
     * @param userId 用户唯一标识 ID
     * @return 账户可用积分值
     */
    @Override
    @PostMapping("query_user_credit_account")
    public Response<BigDecimal> queryUserCreditAccount(String userId) {
        try {
            log.info("查询用户积分值开始 userId:{}", userId);

            // 调用积分调整服务查询账户实体
            CreditAccountEntity creditAccountEntity = creditAdjustService.queryUserCreditAccount(userId);

            log.info("查询用户积分值完成 userId:{} balance:{}", userId, creditAccountEntity.getAdjustAmount());

            // 返回成功响应及积分数值
            return Response
                    .<BigDecimal>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(creditAccountEntity.getAdjustAmount())
                    .build();

        } catch (Exception e) {
            log.error("查询用户积分值失败 userId:{}", userId, e);
            return Response
                    .<BigDecimal>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    /**
     * 积分兑换商品 SKU
     *
     * @param request 购物车请求对象（用户ID、SKU）
     * @return 兑换结果布尔值
     */
    @Override
    @PostMapping("credit_pay_exchange_sku")

    public Response<Boolean> creditPayExchangeSku(SkuProductShopCartRequestDTO request) {
        String userId = request.getUserId();
        Long sku = request.getSku();
        // 1. 外部单号建议由前端传入或在最外层生成，用于整个链路的幂等追踪
        String outBusinessNo = StringUtils.isBlank(request.getOutBusinessNo()) ? RandomStringUtils.randomNumeric(12)
                : request.getOutBusinessNo();

        try {
            log.info("积分兑换商品开始 userId:{} sku:{} outBusinessNo:{}", userId, sku, outBusinessNo);

            // 2. 创建兑换订单：内部包含对一个月内未支付订单的校验及复用逻辑
            SkuRechargeEntity skuRechargeEntity = SkuRechargeEntity
                    .builder()
                    .userId(userId)
                    .sku(sku)
                    .outBusinessNo(outBusinessNo)
                    .orderTradeType(OrderTradeTypeVO.CREDIT_PAY_TRADE)
                    .build();

            UnpaidActivityOrderEntity unpaidActivityOrder =
                    raffleActivityAccountQuotaService.createOrder(skuRechargeEntity);

            // 这里必须复用 query/create 阶段返回的真实单号（可能是一个月内的老订单单号）
            String actualOutBusinessNo = unpaidActivityOrder.getOutBusinessNo();
            log.info("积分兑换商品-创建/获取活动订单完成 userId:{} outBusinessNo:{} actualOutBusinessNo:{}", userId, outBusinessNo,
                    actualOutBusinessNo);

            // 3. 构造积分支付交易请求
            TradeEntity tradeEntity = TradeEntity
                    .builder()
                    .userId(userId)
                    .tradeName(TradeNameVO.CONVERT_SKU)
                    .tradeType(TradeTypeVO.REVERSE)
                    .tradeAmount(unpaidActivityOrder.getPayAmount())
                    .outBusinessNo(actualOutBusinessNo) // 使用最终业务单号
                    .build();

            // 4. 执行积分账户扣减（积分支付动作）
            String creditOrderId = creditAdjustService.createOrder(tradeEntity);
            log.info("积分兑换商品-支付确认完成 userId:{} actualOutBusinessNo:{} creditOrderId:{}", userId, actualOutBusinessNo,
                    creditOrderId);

            return Response
                    .<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(true)
                    .build();

        } catch (AppException e) {
            log.error("积分兑换商品业务异常 userId:{} sku:{} code:{} info:{}", userId, sku, e.getCode(), e.getInfo());
            return Response
                    .<Boolean>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .data(false)
                    .build();
        } catch (Exception e) {
            log.error("积分兑换商品系统未知错误 userId:{} sku:{}", userId, sku, e);
            return Response
                    .<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(false)
                    .build();
        }
    }
}