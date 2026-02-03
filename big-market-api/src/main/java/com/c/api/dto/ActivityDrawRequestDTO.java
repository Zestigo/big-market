package com.c.api.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 活动抽奖请求 DTO
 *
 * @author cyh
 * @since 2026/02/02
 */
@Data
public class ActivityDrawRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户唯一 ID (必填) */
    private String userId;

    /** 活动主键 ID (必填) */
    private Long activityId;

}