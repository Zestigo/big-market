package com.c.infrastructure.po;

import lombok.Data;
import java.util.Date;

/**
 * 任务持久化对象（本地消息表）
 * 职责：支持“可靠消息最终一致性”方案。在业务操作与消息发送之间建立中转，
 * 配合分布式事务中的本地消息表模式，确保消息不丢失、不重复发送。
 *
 * @author cyh
 * @date 2026/02/01
 */
@Data
public class Task {

    /** 任务唯一标识（UUID或分布式ID） */
    private String id;

    /** 消息主题（MQ Topic） */
    private String topic;

    /** 消息主体（通常为 JSON 格式的业务报文） */
    private String message;

    /** 任务状态；create-已创建、completed-处理完成、fail-处理失败 */
    private String state;

    /** 创建时间 */
    private Date createTime;

    /** 更新时间 */
    private Date updateTime;

}