package com.c.infrastructure.dao.po;

import lombok.Data;

import java.util.Date;

/**
 * 战略
 *
 * @author cyh
 * @date 2026/01/15
 */
@Data // lombok getter/setter
public class Strategy {
    private Long id;
    private String strategyId;
    private String strategyDesc;
    private Date createTime;
    private Date updateTime;
}
