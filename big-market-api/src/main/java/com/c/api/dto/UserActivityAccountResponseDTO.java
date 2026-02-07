package com.c.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 用户活动账户响应 DTO
 *
 * @author cyh
 * @date 2026/02/07
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserActivityAccountResponseDTO implements Serializable {

    /** 总次数限制 */
    private Integer totalCount;

    /** 总次数剩余 */
    private Integer totalCountSurplus;

    /** 日次数限制 */
    private Integer dayCount;

    /** 日次数剩余 */
    private Integer dayCountSurplus;

    /** 月次数限制 */
    private Integer monthCount;

    /** 月次数剩余 */
    private Integer monthCountSurplus;

}