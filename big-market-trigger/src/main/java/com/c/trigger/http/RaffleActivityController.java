package com.c.trigger.http;

import com.c.api.IRaffleActivityService;
import com.c.api.dto.*;
import com.c.domain.activity.model.entity.UserRaffleOrderEntity;
import com.c.domain.activity.service.IRaffleActivityPartakeService;
import com.c.domain.activity.service.armory.IActivityArmory;
import com.c.domain.award.model.entity.UserAwardRecordEntity;
import com.c.domain.award.model.vo.AwardStateVO;
import com.c.domain.award.service.IAwardService;
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
import java.util.Date;

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

    /**
     * 活动装配预热接口
     * 逻辑：通过活动 ID 触发活动 SKU、库存、抽奖策略以及概率权重矩阵的缓存加载。
     *
     * @param activityId 活动配置 ID
     * @return Response<Boolean> 装配成功返回 true
     */
    @Override
    @GetMapping("armory")
    public Response<Boolean> armory(@RequestParam Long activityId) {
        try {
            log.info("活动装配预热开始，activityId:{}", activityId);

            // 1. 装配活动 SKU 及其关联的库存信息
            activityArmory.assembleActivitySkuByActivityId(activityId);

            // 2. 装配抽奖策略及其对应的概率权重矩阵
            strategyArmory.assembleLotteryStrategyByActivityId(activityId);

            log.info("活动装配预热完成，activityId:{}", activityId);
            return Response.<Boolean>builder().code(ResponseCode.SUCCESS.getCode())
                           .info(ResponseCode.SUCCESS.getInfo()).data(true).build();
        } catch (Exception e) {
            log.error("活动装配预热失败，activityId:{}", activityId, e);
            return Response.<Boolean>builder().code(ResponseCode.UN_ERROR.getCode())
                           .info(ResponseCode.UN_ERROR.getInfo()).build();
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
        try {
            log.info("抽奖行为触发，userId:{} activityId:{}", request.getUserId(), request.getActivityId());

            // 1. 参数合法性前置校验
            if (StringUtils.isBlank(request.getUserId()) || null == request.getActivityId()) {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(),
                        ResponseCode.ILLEGAL_PARAMETER.getInfo());
            }

            // 2. 参与活动：扣减额度并创建用户抽奖参与订单（含重入性检查）
            UserRaffleOrderEntity orderEntity =
                    raffleActivityPartakeService.createOrder(request.getUserId(), request.getActivityId());
            log.info("用户参与活动成功，生成订单号:{}", orderEntity.getOrderId());

            // 3. 执行抽奖决策：基于策略引擎计算中奖结果
            RaffleAwardEntity raffleAwardEntity = raffleStrategy.performRaffle(RaffleFactorEntity.builder()
                                                                                                 .userId(orderEntity.getUserId())
                                                                                                 .strategyId(orderEntity.getStrategyId())
                                                                                                 .build());

            // 4. 结果异步化/落库：保存中奖记录，等待后续发奖流程
            UserAwardRecordEntity userAwardRecord = UserAwardRecordEntity.builder()
                                                                         .userId(orderEntity.getUserId())
                                                                         .activityId(orderEntity.getActivityId())
                                                                         .strategyId(orderEntity.getStrategyId())
                                                                         .orderId(orderEntity.getOrderId())
                                                                         .awardId(raffleAwardEntity.getAwardId())
                                                                         .awardTitle(raffleAwardEntity.getAwardTitle())
                                                                         .awardTime(new Date())
                                                                         .awardState(AwardStateVO.create)
                                                                         .build();
            awardService.saveUserAwardRecord(userAwardRecord);

            // 5. 组装响应数据并返回
            return Response.<ActivityDrawResponseDTO>builder().code(ResponseCode.SUCCESS.getCode())
                           .info(ResponseCode.SUCCESS.getInfo())
                           .data(ActivityDrawResponseDTO.builder().awardId(raffleAwardEntity.getAwardId())
                                                        .awardTitle(raffleAwardEntity.getAwardTitle())
                                                        .awardIndex(raffleAwardEntity.getSort()).build())
                           .build();

        } catch (AppException e) {
            log.error("抽奖业务异常，userId:{} activityId:{}", request.getUserId(), request.getActivityId(), e);
            return Response.<ActivityDrawResponseDTO>builder().code(e.getCode()).info(e.getInfo()).build();
        } catch (Exception e) {
            log.error("抽奖系统未知错误，userId:{} activityId:{}", request.getUserId(), request.getActivityId(), e);
            return Response.<ActivityDrawResponseDTO>builder().code(ResponseCode.UN_ERROR.getCode())
                           .info(ResponseCode.UN_ERROR.getInfo()).build();
        }
    }
}