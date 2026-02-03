package com.c.trigger.http;

import com.c.api.IRaffleStrategyService;
import com.c.api.dto.RaffleAwardListRequestDTO;
import com.c.api.dto.RaffleAwardListResponseDTO;
import com.c.api.dto.RaffleStrategyRequestDTO;
import com.c.api.dto.RaffleStrategyResponseDTO;
import com.c.domain.strategy.model.entity.RaffleAwardEntity;
import com.c.domain.strategy.model.entity.RaffleFactorEntity;
import com.c.domain.strategy.model.entity.StrategyAwardEntity;
import com.c.domain.strategy.service.IRaffleAward;
import com.c.domain.strategy.service.IRaffleStrategy;
import com.c.domain.strategy.service.armory.IStrategyArmory;
import com.c.types.enums.ResponseCode;
import com.c.types.model.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 抽奖服务控制器
 * 职责：作为触发器层（Trigger），负责接收外部 HTTP 请求，调用领域层服务，并封装标准的响应结果。
 * *
 *
 * @author cyh
 * @date 2026/01/23
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

    /**
     * 策略装配：预热概率表与库存
     * GET: /api/v1/raffle/strategy_armory?strategyId=100006
     */
    @Override
    @GetMapping("armory")
    public Response<Boolean> strategyArmory(@RequestParam Long strategyId) {
        try {
            log.info("抽奖策略装配开始 strategyId:{}", strategyId);
            boolean armoryStatus = strategyArmory.assembleLotteryStrategy(strategyId);
            return Response.<Boolean>builder().code(ResponseCode.SUCCESS.getCode())
                           .info(ResponseCode.SUCCESS.getInfo()).data(armoryStatus).build();
        } catch (Exception e) {
            log.error("抽奖策略装配失败 strategyId:{}", strategyId, e);
            return Response.<Boolean>builder().code(ResponseCode.UN_ERROR.getCode())
                           .info(ResponseCode.UN_ERROR.getInfo()).build();
        }
    }

    /**
     * 查询抽奖奖品列表
     * POST: /api/v1/raffle/query_raffle_award_list
     */
    @Override
    @PostMapping("query_raffle_award_list")
    public Response<List<RaffleAwardListResponseDTO>> queryRaffleAwardList(@RequestBody RaffleAwardListRequestDTO requestDTO) {
        try {
            log.info("查询奖品列表开始 strategyId:{}", requestDTO.getStrategyId());
            // 1. 调用领域服务查询实体
            List<StrategyAwardEntity> strategyAwardEntities =
                    raffleAward.queryRaffleStrategyAwardList(requestDTO.getStrategyId());

            // 2. 使用 Stream 流进行模型转换 (Entity -> DTO)，减少模板代码
            List<RaffleAwardListResponseDTO> responseDTOList = strategyAwardEntities.stream()
                                                                                    .map(entity -> RaffleAwardListResponseDTO
                                                                                            .builder()
                                                                                            .awardId(entity.getAwardId())
                                                                                            .awardTitle(entity.getAwardTitle())
                                                                                            .awardSubtitle(entity.getAwardSubtitle())
                                                                                            .sort(entity.getSort())
                                                                                            .build())
                                                                                    .collect(Collectors.toList());

            log.info("查询奖品列表完成 strategyId:{} count:{}", requestDTO.getStrategyId(), responseDTOList.size());
            return Response.<List<RaffleAwardListResponseDTO>>builder().code(ResponseCode.SUCCESS.getCode())
                           .info(ResponseCode.SUCCESS.getInfo()).data(responseDTOList).build();
        } catch (Exception e) {
            log.error("查询奖品列表失败 strategyId:{}", requestDTO.getStrategyId(), e);
            return Response.<List<RaffleAwardListResponseDTO>>builder().code(ResponseCode.UN_ERROR.getCode())
                           .info(ResponseCode.UN_ERROR.getInfo()).build();
        }
    }

    /**
     * 随机抽奖接口
     * POST: /api/v1/raffle/random_raffle
     */
    @Override
    @PostMapping("random_raffle")
    public Response<RaffleStrategyResponseDTO> randomRaffle(@RequestBody RaffleStrategyRequestDTO requestDTO) {
        try {
            log.info("随机抽奖开始 strategyId: {}", requestDTO.getStrategyId());

            // 1. 组装抽奖因子（后续 userId 应从 Token 或 Session 中动态获取）
            RaffleFactorEntity factor = RaffleFactorEntity.builder().userId("system")
                                                          .strategyId(requestDTO.getStrategyId()).build();

            // 2. 执行抽奖领域逻辑
            RaffleAwardEntity raffleAwardEntity = raffleStrategy.performRaffle(factor);

            // 3. 封装返回结果
            RaffleStrategyResponseDTO responseDTO = RaffleStrategyResponseDTO.builder()
                                                                             .awardId(raffleAwardEntity.getAwardId())
                                                                             .awardIndex(raffleAwardEntity.getSort())
                                                                             .build();

            log.info("随机抽奖完成 strategyId: {} awardId: {}", requestDTO.getStrategyId(),
                    responseDTO.getAwardId());
            return Response.<RaffleStrategyResponseDTO>builder().code(ResponseCode.SUCCESS.getCode())
                           .info(ResponseCode.SUCCESS.getInfo()).data(responseDTO).build();
        } catch (Exception e) {
            log.error("随机抽奖失败 strategyId: {}", requestDTO.getStrategyId(), e);
            return Response.<RaffleStrategyResponseDTO>builder().code(ResponseCode.UN_ERROR.getCode())
                           .info(ResponseCode.UN_ERROR.getInfo()).build();
        }
    }
}