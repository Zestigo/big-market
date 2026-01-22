package com.c.infrastructure.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Strategy {
    private Long id;
    private Long strategyId;
    private String strategyDesc;
    private String ruleModels;
    private Date createTime;
    private Date updateTime;
}