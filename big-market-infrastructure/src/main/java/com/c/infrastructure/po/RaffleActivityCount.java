package com.c.infrastructure.po;

import lombok.Data;
import java.util.Date;

/**
 * 抽奖活动-参与次数限制配置模板
 * 设计初衷：抽离通用的用户参与次数限制规则为独立模板，实现多活动规则复用，避免重复配置，便于批量修改规则
 * 核心职责：定义单用户参与抽奖活动的「总/日/月」次数限制规则，作为通用模板被RaffleActivitySKU关联引用
 *
 * @author cyh
 * @date 2026/01/25
 */
@Data
public class RaffleActivityCount {

    /** 数据库自增主键ID */
    private Long id;

    /** 本配置模板的业务唯一标识，作为外键被RaffleActivitySKU.activityCountId关联 */
    private Long activityCountId;

    /** 单用户对该规则模板的「终身总参与次数限制」，针对单个用户独立计数 */
    private Integer totalCount;

    /** 单用户对该规则模板的「单日参与次数限制」，针对单个用户独立计数 */
    private Integer dayCount;

    /** 单用户对该规则模板的「单月参与次数限制」，针对单个用户独立计数 */
    private Integer monthCount;

    /** 记录创建时间 */
    private Date createTime;

    /** 记录最后更新时间 */
    private Date updateTime;

}