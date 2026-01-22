package com.c.infrastructure.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Award {
    private Long id;
    private Integer awardId;
    private String awardKey;
    private String awardConfig;
    private String awardDesc;
    private Date createTime;
    private Date updateTime;
}
