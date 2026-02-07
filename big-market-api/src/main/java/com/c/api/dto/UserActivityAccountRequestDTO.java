package com.c.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 用户活动账户请求 DTO
 *
 * @author cyh
 * @date 2026/02/07
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserActivityAccountRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户唯一ID */
    private String userId;

    /** 活动唯一ID */
    private Long activityId;

}