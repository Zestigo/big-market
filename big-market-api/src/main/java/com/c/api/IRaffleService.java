package com.c.api;

import com.c.api.dto.RaffleAwardListRequestDTO;
import com.c.api.dto.RaffleAwardListResponseDTO;
import com.c.api.dto.RaffleRequestDTO;
import com.c.api.dto.RaffleResponseDTO;
import com.c.api.response.Response;

import java.util.List;

public interface IRaffleService {
    Response<Boolean> strategyArmoy(Long strategyId);

    Response<List<RaffleAwardListResponseDTO>> queryRaffleAwardList(RaffleAwardListRequestDTO requestDTO);

    Response<RaffleResponseDTO> randomRaffle(RaffleRequestDTO requestDTO);
}
