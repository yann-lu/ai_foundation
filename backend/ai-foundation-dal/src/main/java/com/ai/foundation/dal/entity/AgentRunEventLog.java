package com.ai.foundation.dal.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_run_event_log")
public class AgentRunEventLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long runId;

    private Long conversationId;

    private String eventType;

    private String taskState;

    private String eventData;

    private Integer seqNo;

    private Long eventTimestamp;

    @TableLogic
    private Integer isDelete;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
