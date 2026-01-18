package com.c.domain.strategy.service;

import com.c.domain.strategy.model.entity.RaffleAwardEntity;
import com.c.domain.strategy.model.entity.RaffleFactorEntity;

public interface IRaffleStrategy {
    RaffleAwardEntity performRaffle(RaffleFactorEntity raffleFactor);
}
