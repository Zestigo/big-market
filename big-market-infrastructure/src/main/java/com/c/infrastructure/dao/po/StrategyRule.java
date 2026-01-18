package com.c.infrastructure.dao.po;

import com.c.types.common.Constants;
import lombok.*;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StrategyRule {
    private Long id;
    private Long strategyId;
    private Integer awardId;
    private Integer ruleType;
    private String ruleModel;
    /** 规则值；积分权重示例："100:1,2,3 200:4,5,6" */
    private String ruleValue;
    private String ruleDesc;
    private Date createTime;
    private Date updateTime;
}