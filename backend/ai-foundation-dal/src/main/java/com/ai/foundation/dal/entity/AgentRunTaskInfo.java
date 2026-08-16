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
@TableName("agent_run_task_info")
public class AgentRunTaskInfo {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long runId;

    private String taskCode;

    private String taskType;

    private String capabilityType;

    private Long refId;

    private String refName;

    private String instruction;

    private String inputParams;

    private String resultRef;

    private String taskState;

    private String errorMessage;

    private Long costMs;

    @TableLogic
    private Integer isDelete;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
