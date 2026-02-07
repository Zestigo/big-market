package com.c.trigger.http;

import com.c.api.IRaffleStrategyService;
import com.c.api.dto.*;
import com.c.domain.activity.service.IRaffleActivityAccountQuotaService;
import com.c.domain.strategy.model.entity.RaffleAwardEntity;
import com.c.domain.strategy.model.entity.RaffleFactorEntity;
import com.c.domain.strategy.model.entity.StrategyAwardEntity;
import com.c.domain.strategy.model.vo.RuleWeightVO;
import com.c.domain.strategy.service.IRaffleAward;
import com.c.domain.strategy.service.IRaffleRule;
import com.c.domain.strategy.service.IRaffleStrategy;
import com.c.domain.strategy.service.armory.IStrategyArmory;
import com.c.types.enums.ResponseCode;
import com.c.types.exception.AppException;
import com.c.types.model.Response;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 抽奖策略 HTTP 服务接口
 * 职责：
 * 1. 协议转换：负责将外部标准的 Restful 请求映射为内部领域模型。
 * 2. 流程编排：聚合活动额度领域、策略领域，实现复杂的业务判断（如奖品锁定状态）。
 * 3. 异常处理：统一捕获业务异常与系统异常，封装标准的返回码。
 *
 * @author cyh
 * @since 2026/01/23
 */
@Slf4j
@RestController
@CrossOrigin("${app.config.cross-origin}")
@RequestMapping("/api/${app.config.api-version}/raffle/strategy/")
public class RaffleStrategyController implements IRaffleStrategyService {

    @Resource
    private IStrategyArmory strategyArmory;
    @Resource
    private IRaffleAward raffleAward;
    @Resource
    private IRaffleStrategy raffleStrategy;
    @Resource
    private IRaffleRule raffleRule;
    @Resource
    private IRaffleActivityAccountQuotaService raffleActivityAccountQuotaService;

    /**
     * 策略装配接口 - 预热内存/缓存概率数据
     * 业务逻辑：将数据库中的随机概率配置加载至 Redis 抽奖池，通过分块（Sharding）算法实现 O(1) 复杂度的中奖计算。
     *
     * @param strategyId 策略 ID（在活动中关联的唯一抽奖配置 ID）
     * @return 装配执行结果
     */
    @Override
    @GetMapping("armory")
    public Response<Boolean> strategyArmory(@RequestParam Long strategyId) {
        try {
            log.info("抽奖策略装配开始，策略ID: {}", strategyId);
            // 1. 调用策略装配工厂，计算概率表并存入 Redis
            boolean armoryStatus = strategyArmory.assembleLotteryStrategy(strategyId);
            return Response
                    .<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(armoryStatus)
                    .build();
        } catch (Exception e) {
            log.error("抽奖策略装配失败，策略ID: {}", strategyId, e);
            return Response
                    .<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    /**
     * 查询抽奖奖品列表（含用户解锁进度）
     * 业务背景：部分奖品（如大奖）需达到一定抽奖次数后才可获得。此接口返回奖品明细及用户的解锁状态。
     *
     * @param request 包含 activityId（活动ID）和 userId（用户唯一标识）
     * @return 奖品展示列表 DTO
     */
    @Override
    @PostMapping("query_raffle_award_list")
    public Response<List<RaffleAwardListResponseDTO>> queryRaffleAwardList(@RequestBody RaffleAwardListRequestDTO request) {
        try {
            Long activityId = request.getActivityId();
            String userId = request.getUserId();
            log.info("查询抽奖奖品列表开始，活动ID: {}，用户ID: {}", activityId, userId);

            // 1. 参数严谨校验
            if (null == activityId || StringUtils.isBlank(userId)) {
                return Response
                        .<List<RaffleAwardListResponseDTO>>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info(ResponseCode.ILLEGAL_PARAMETER.getInfo())
                        .build();
            }

            // 2. 从领域服务中查询策略奖品的基础配置信息
            List<StrategyAwardEntity> strategyAwardEntities =
                    raffleAward.queryRaffleStrategyAwardListByActivityId(activityId);

            // 3. 提取奖品关联的规则树 ID（用于查询 rule_lock 锁定次数）
            String[] treeIds = strategyAwardEntities
                    .stream()
                    .map(StrategyAwardEntity::getRuleModels)
                    .filter(StringUtils::isNotBlank)
                    .toArray(String[]::new);

            // 4. 查询业务进度：获取奖品规则锁定门槛 & 用户今日累计参与次数
            Map<String, Integer> ruleLockCountMap = raffleRule.queryAwardRuleLockCount(treeIds);
            Integer dayPartakeCount =
                    raffleActivityAccountQuotaService.queryRaffleActivityAccountDayPartakeCount(activityId, userId);

            // 5. 将领域实体映射为 Response DTO，并判定解锁状态
            List<RaffleAwardListResponseDTO> responseDTOs = strategyAwardEntities
                    .stream()
                    .map(strategyAward -> {
                        Integer lockCount = ruleLockCountMap.getOrDefault(strategyAward.getRuleModels(), 0);
                        return RaffleAwardListResponseDTO
                                .builder()
                                .awardId(strategyAward.getAwardId())
                                .awardTitle(strategyAward.getAwardTitle())
                                .awardSubtitle(strategyAward.getAwardSubtitle())
                                .awardRuleLockCount(lockCount)
                                .isAwardUnlock(dayPartakeCount >= lockCount)
                                .waitUnlockCount(dayPartakeCount < lockCount ? lockCount - dayPartakeCount : 0)
                                .sort(strategyAward.getSort())
                                .build();
                    })
                    .collect(Collectors.toList());

            log.info("查询抽奖奖品列表完成，返回奖品数量: {}", responseDTOs.size());
            return Response
                    .<List<RaffleAwardListResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTOs)
                    .build();
        } catch (Exception e) {
            log.error("查询抽奖奖品列表异常，活动ID: {}", request.getActivityId(), e);
            return Response
                    .<List<RaffleAwardListResponseDTO>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    /**
     * 随机抽奖执行接口
     * 执行链路：1.职责链(过滤) -> 2.库内概率计算 -> 3.决策树(库存与门槛) -> 4.结果派发
     *
     * @param requestDTO 包含 strategyId（策略ID）
     * @return 中奖结果及展示信息
     */
    @Override
    @PostMapping("random_raffle")
    public Response<RaffleStrategyResponseDTO> randomRaffle(@RequestBody RaffleStrategyRequestDTO requestDTO) {
        try {
            log.info("随机抽奖开始，执行策略ID: {}", requestDTO.getStrategyId());

            // 1. 构建抽奖因子模型
            RaffleFactorEntity factor = RaffleFactorEntity
                    .builder()
                    .userId("system") // TODO: 接入鉴权体系后动态获取
                    .strategyId(requestDTO.getStrategyId())
                    .build();

            // 2. 执行核心抽奖领域服务
            RaffleAwardEntity raffleAwardEntity = raffleStrategy.performRaffle(factor);

            // 3. 结果封装及转换
            RaffleStrategyResponseDTO responseDTO = RaffleStrategyResponseDTO
                    .builder()
                    .awardId(raffleAwardEntity.getAwardId())
                    .awardIndex(raffleAwardEntity.getSort())
                    .build();

            log.info("随机抽奖成功，策略ID: {}，中奖奖品ID: {}", requestDTO.getStrategyId(), responseDTO.getAwardId());
            return Response
                    .<RaffleStrategyResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTO)
                    .build();
        } catch (AppException e) {
            log.error("随机抽奖业务异常，策略ID: {}，错误码: {}，描述: {}", requestDTO.getStrategyId(), e.getCode(), e.getInfo());
            return Response
                    .<RaffleStrategyResponseDTO>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("随机抽奖系统异常，策略ID: {}", requestDTO.getStrategyId(), e);
            return Response
                    .<RaffleStrategyResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    /**
     * 查询抽奖策略权重规则配置
     * 业务场景：在页面展示“积分抽奖”等带权重的配置项，告知用户抽奖 N 次可锁定某组奖品。
     *
     * @param request 包含 activityId（活动ID）和 userId（用户唯一标识）
     * @return 权重规则配置列表（含用户当前总进度）
     */
    @Override
    @PostMapping("query_raffle_strategy_rule_weight")
    public Response<List<RaffleStrategyRuleWeightResponseDTO>> queryRaffleStrategyRuleWeight(@RequestBody RaffleStrategyRuleWeightRequestDTO request) {
        String userId = request.getUserId();
        Long activityId = request.getActivityId();
        try {
            log.info("查询抽奖策略权重规则配置开始 userId:{} activityId:{}", userId, activityId);

            // 1. 参数校验
            if (StringUtils.isBlank(userId) || null == activityId) {
                return Response
                        .<List<RaffleStrategyRuleWeightResponseDTO>>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info(ResponseCode.ILLEGAL_PARAMETER.getInfo())
                        .build();
            }

            // 2. 查询用户在当前活动下的累计参与次数（用于匹配权重进度）
            Integer totalUseCount =
                    raffleActivityAccountQuotaService.queryRaffleActivityAccountPartakeCount(activityId, userId);

            // 3. 查询权重规则配置领域实体
            List<RuleWeightVO> ruleWeightVOList = raffleRule.queryAwardRuleWeightByActivityId(activityId);

            // 4. 将领域模型 VO 转换为响应 DTO，并补全奖品子列表
            List<RaffleStrategyRuleWeightResponseDTO> responseDTOList = ruleWeightVOList
                    .stream()
                    .map(vo -> {
                        List<RaffleStrategyRuleWeightResponseDTO.StrategyAward> awards = vo
                                .getAwardList()
                                .stream()
                                .map(award -> RaffleStrategyRuleWeightResponseDTO.StrategyAward
                                        .builder()
                                        .awardId(award.getAwardId())
                                        .awardTitle(award.getAwardTitle())
                                        .build())
                                .collect(Collectors.toList());

                        return RaffleStrategyRuleWeightResponseDTO
                                .builder()
                                .ruleWeightCount(vo.getWeight())
                                .userActivityTotalCount(totalUseCount)
                                .strategyAwards(awards)
                                .build();
                    })
                    .collect(Collectors.toList());

            log.info("查询抽奖策略权重规则配置完成，活动ID: {}，组装结果条数: {}", activityId, responseDTOList.size());
            return Response
                    .<List<RaffleStrategyRuleWeightResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTOList)
                    .build();

        } catch (Exception e) {
            log.error("查询抽奖策略权重规则配置失败 userId:{} activityId:{}", userId, activityId, e);
            return Response
                    .<List<RaffleStrategyRuleWeightResponseDTO>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }
}