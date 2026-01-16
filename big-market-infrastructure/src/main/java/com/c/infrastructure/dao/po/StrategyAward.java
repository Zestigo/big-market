package com.c.infrastructure.dao.po;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 战略奖
 *
 * @author cyh
 * @date 2026/01/15
 */
@Data
public class StrategyAward {
    /** 身份证 */
    private Long id;
    /** 策略ID */
    private Long strategyId;
    /** 奖项ID */
    private Integer awardId;
    /** 奖项名称 */
    private String awardTitle;
    /** 奖项副标题 */
    private String awardSubTitle;
    /** 奖项统计 */
    private Integer awardCount;
    /** 奖励计数盈余 */
    private Integer awardCountSurplus;
    /** 奖项等级 */
    private BigDecimal awardRate;
    /** 规则模型 */
    private String ruleModels;
    /** 分类 */
    private Integer sort;
    /** 创造时间 */
    private Date createTime;
    /** 更新时间 */
    private Date updateTime;
}
