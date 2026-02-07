package com.c.infrastructure.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 任务持久化对象：本地消息表
 * * 职责：作为分布式事务中“最终一致性”方案的核心载体。
 * 作用：在业务事务中同步记录消息任务，由异步补偿任务或事务后置操作读取发送，
 * 解决数据库操作与 MQ 发送之间的原子性难题。
 *
 * @author cyh
 * @date 2026/02/01
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Task {

    /** 自增 ID 或分布式主键 */
    private Long id;

    /** 用户唯一标识：用于业务回溯与分库分表路由键 */
    private String userId;

    /** 消息队列交换机名称：指定消息投递的逻辑入口 */
    private String exchange;

    /** 消息队列路由键：指定消息精准分发的匹配规则 */
    private String routingKey;

    /** 消息唯一编号：用于生产端 Confirm 确认与消费端幂等校验 */
    private String messageId;

    /** 消息主体：序列化后的业务报文（JSON 格式） */
    private String message;

    /** 任务状态：CREATE-已创建、COMPLETED-处理完成、fail-处理失败 */
    private String state;

    /** 创建时间：记录记录任务生成的初始时刻 */
    private Date createTime;

    /** 更新时间：记录任务状态变更的最后时刻，用于补偿 Job 扫描 */
    private Date updateTime;

}