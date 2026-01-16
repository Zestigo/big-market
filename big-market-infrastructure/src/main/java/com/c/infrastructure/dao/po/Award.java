package com.c.infrastructure.dao.po;

import lombok.Data;

import java.util.Date;

/**
 * 奖
 *
 * @author hafutpurs
 * @date 2026/01/15
 */
@Data
public class Award {
    private Long id;
    /** 奖项编号 */
    private Integer awardId;
    /** 奖励钥匙 */
    private String awardKey;
    /** 奖项配置 */
    private String awardConfig;
    /** 奖项描述 */
    private String awardDesc;
    /** 创造时间 */
    private Date createTime;
    /** 更新时间 */
    private Date updateTime;
}
