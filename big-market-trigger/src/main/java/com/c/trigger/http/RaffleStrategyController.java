package com.c.trigger.http;

import com.c.api.IRaffleStrategyService;
import com.c.api.dto.RaffleAwardListRequestDTO;
import com.c.api.dto.RaffleAwardListResponseDTO;
import com.c.api.dto.RaffleStrategyRequestDTO;
import com.c.api.dto.RaffleStrategyResponseDTO;
import com.c.domain.activity.service.IRaffleActivityAccountQuotaService;
import com.c.domain.strategy.model.entity.RaffleAwardEntity;
import com.c.domain.strategy.model.entity.RaffleFactorEntity;
import com.c.domain.strategy.model.entity.StrategyAwardEntity;
import com.c.domain.strategy.service.IRaffleAward;
import com.c.domain.strategy.service.IRaffleRule;
import com.c.domain.strategy.service.IRaffleStrategy;
import com.c.domain.strategy.service.armory.IStrategyArmory;
import com.c.types.enums.ResponseCode;
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
     * 业务背景：抽奖系统为了高性能，不直接查询数据库。在活动上线前，需将概率分布表计算好并加载至 Redis。
     *
     * @param strategyId 策略 ID（在活动中关联的唯一抽奖配置 ID）
     * @return Response<Boolean> 装配成功返回 true，否则返回 false
     */
    @Override
    @GetMapping("armory")
    public Response<Boolean> strategyArmory(@RequestParam Long strategyId) {
        try {
            log.info("抽奖策略装配开始，策略ID: {}", strategyId);
            // 调用策略装配工厂，计算概率表并存入 Redis
            boolean armoryStatus = strategyArmory.assembleLotteryStrategy(strategyId);
            return Response.<Boolean>builder().code(ResponseCode.SUCCESS.getCode()).info(ResponseCode.SUCCESS.getInfo())
                           .data(armoryStatus).build();
        } catch (Exception e) {
            log.error("抽奖策略装配失败，策略ID: {}", strategyId, e);
            return Response.<Boolean>builder().code(ResponseCode.UN_ERROR.getCode())
                           .info(ResponseCode.UN_ERROR.getInfo()).build();
        }
    }

    /**
     * 查询抽奖奖品列表（含用户解锁进度）
     * 业务逻辑：
     * 1. 查奖品：获取活动对应的全量奖品。
     * 2. 查门槛：获取奖品配置的规则锁（如：抽 5 次才解锁某奖品）。
     * 3. 查用户：获取用户当日已抽奖的次数。
     * 4. 算状态：对比用户次数与锁定门槛，标记奖品是否“可获得”。
     *
     * @param request 包含 activityId（活动ID）和 userId（用户唯一标识）
     * @return Response<List < RaffleAwardListResponseDTO>> 奖品展示列表
     */
    @Override
    @PostMapping("query_raffle_award_list")
    public Response<List<RaffleAwardListResponseDTO>> queryRaffleAwardList(@RequestBody RaffleAwardListRequestDTO request) {
        try {
            // 获取并校验关键参数
            Long activityId = request.getActivityId();
            String userId = request.getUserId();
            log.info("查询抽奖奖品列表开始，活动ID: {}，用户ID: {}", activityId, userId);

            if (null == activityId || StringUtils.isBlank(userId)) {
                return Response.<List<RaffleAwardListResponseDTO>>builder()
                               .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                               .info(ResponseCode.ILLEGAL_PARAMETER.getInfo()).build();
            }

            // Step 1: 从领域服务中查询策略奖品的基础配置信息
            List<StrategyAwardEntity> strategyAwardEntities =
                    raffleAward.queryRaffleStrategyAwardListByActivityId(activityId);

            // Step 2: 提取奖品关联的规则树 ID（用于查询 rule_lock 锁定次数）
            String[] treeIds = strategyAwardEntities.stream().map(StrategyAwardEntity::getRuleModels)
                                                    .filter(StringUtils::isNotBlank).toArray(String[]::new);

            // Step 3: 并发/顺序查询业务数据：1.规则锁定的次数 2.用户今日已参与次数
            Map<String, Integer> ruleLockCountMap = raffleRule.queryAwardRuleLockCount(treeIds);
            Integer dayPartakeCount =
                    raffleActivityAccountQuotaService.queryRaffleActivityAccountDayPartakeCount(activityId, userId);

            // Step 4: 将领域实体转换为展示层 DTO，并执行核心业务逻辑判断（是否解锁）
            List<RaffleAwardListResponseDTO> responseDTOs = strategyAwardEntities.stream().map(strategyAward -> {
                // 获取当前奖品的锁定门槛（若无配置则为 0）
                Integer lockCount = ruleLockCountMap.getOrDefault(strategyAward.getRuleModels(), 0);

                return RaffleAwardListResponseDTO.builder().awardId(strategyAward.getAwardId())
                                                 .awardTitle(strategyAward.getAwardTitle())
                                                 .awardSubtitle(strategyAward.getAwardSubtitle())
                                                 // 该奖品解锁需要的次数
                                                 .awardRuleLockCount(lockCount)
                                                 // 用户次数 >= 门槛次数，则解锁
                                                 .isAwardUnlock(dayPartakeCount >= lockCount)
                                                 // 还差几次解锁
                                                 .waitUnlockCount(dayPartakeCount < lockCount ?
                                                         lockCount - dayPartakeCount : 0)
                                                 .sort(strategyAward.getSort()).build();
            }).collect(Collectors.toList());

            log.info("查询抽奖奖品列表完成，返回奖品数量: {}", responseDTOs.size());
            return Response.<List<RaffleAwardListResponseDTO>>builder().code(ResponseCode.SUCCESS.getCode())
                           .info(ResponseCode.SUCCESS.getInfo()).data(responseDTOs).build();
        } catch (Exception e) {
            log.error("查询抽奖奖品列表异常，活动ID: {}", request.getActivityId(), e);
            return Response.<List<RaffleAwardListResponseDTO>>builder().code(ResponseCode.UN_ERROR.getCode())
                           .info(ResponseCode.UN_ERROR.getInfo()).build();
        }
    }

    /**
     * 随机抽奖执行接口
     * 执行流程：职责链（黑名单/权重） -> 概率随机抽奖 -> 决策树（库存/门槛）
     *
     * @param requestDTO 包含 strategyId（策略ID）
     * @return Response<RaffleStrategyResponseDTO> 中奖结果信息
     */
    @Override
    @PostMapping("random_raffle")
    public Response<RaffleStrategyResponseDTO> randomRaffle(@RequestBody RaffleStrategyRequestDTO requestDTO) {
        try {
            log.info("随机抽奖开始，执行策略ID: {}", requestDTO.getStrategyId());

            // 1. 构建抽奖因子模型（userId 后续应通过 AOP 从 UserContext/Token 中获取）
            RaffleFactorEntity factor = RaffleFactorEntity.builder().userId("system")
                                                          .strategyId(requestDTO.getStrategyId()).build();

            // 2. 调用核心抽奖领域服务：执行职责链、概率计算、决策树等逻辑
            RaffleAwardEntity raffleAwardEntity = raffleStrategy.performRaffle(factor);

            // 3. 结果封装：将中奖实体转换为前端展示所需的 DTO
            RaffleStrategyResponseDTO responseDTO = RaffleStrategyResponseDTO.builder()
                                                                             .awardId(raffleAwardEntity.getAwardId())
                                                                             // 对应前端转盘的索引位
                                                                             .awardIndex(raffleAwardEntity.getSort())
                                                                             .build();

            log.info("随机抽奖成功，策略ID: {}，中奖奖品ID: {}", requestDTO.getStrategyId(), responseDTO.getAwardId());
            return Response.<RaffleStrategyResponseDTO>builder().code(ResponseCode.SUCCESS.getCode())
                           .info(ResponseCode.SUCCESS.getInfo()).data(responseDTO).build();
        } catch (Exception e) {
            log.error("随机抽奖业务异常，策略ID: {}", requestDTO.getStrategyId(), e);
            return Response.<RaffleStrategyResponseDTO>builder().code(ResponseCode.UN_ERROR.getCode())
                           .info(ResponseCode.UN_ERROR.getInfo()).build();
        }
    }
}