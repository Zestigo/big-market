package com.c.infrastructure.dao.po;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 策略规则
 *
 * @author cyh
 * @date 2026/01/15
 */
@Data
public class StrategyRule {
    private Long id;
    private Long strategyId;
    private Integer awardId;
    private Integer ruleType;
    private String ruleModel;
    private String ruleValue;
    private String ruleDesc;
    private Date createTime;
    private Date updateTime;
}
