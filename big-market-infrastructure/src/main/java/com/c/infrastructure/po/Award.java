package com.c.infrastructure.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 持久化对象：奖品配置表 (award)
 * 职责：直接映射数据库 award 表字段，作为数据访问层（DAO）与数据库之间的传输载体。
 * 规范：字段名与数据库列名保持下划线转驼峰的对应关系。
 *
 * @author cyh
 * @date 2026/02/06
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Award {

    /** 自增 ID：数据库物理主键 */
    private Long id;

    /** 奖品 ID：业务层面的唯一标识（如：101, 102） */
    private Integer awardId;

    /** 奖品 Key：用于代码逻辑识别的字符串标识（如：user_credit_random） */
    private String awardKey;

    /** 奖品配置：根据 awardKey 不同，存储不同的 JSON 串或特定格式参数（如：积分值、面额） */
    private String awardConfig;

    /** 奖品描述：后台管理及日志记录使用的友好描述 */
    private String awardDesc;

    /** 创建时间：记录记录生成的时刻 */
    private Date createTime;

    /** 更新时间：记录最后一次修改的时刻 */
    private Date updateTime;

}