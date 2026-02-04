package com.c.domain.strategy.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * @author cyh
 * @description 抽奖奖品实体：封装策略执行后匹配到的奖品配置信息
 * @date 2026/02/02
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RaffleAwardEntity {

    /** 奖品唯一标识 */
    private Integer awardId;

    /** 奖品配套配置：如红包金额、积分数值、或是跳转链接等扩展信息 */
    private String awardConfig;

    /** 奖品标题（名称） */
    private String awardTitle;

    /** 展示排序：用于在抽奖记录或结果列表中控制先后顺序 */
    private Integer sort;

    /** 活动结束时间（用于控制该订单在缓存或库中的生命周期） */
    private Date endDateTime;

}