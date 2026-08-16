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
@TableName("agent_mcp_server")
public class AgentMcpServer {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String serverCode;

    private String serverName;

    private String description;

    private String transportType;

    private String command;

    private String workingDir;

    private String envVars;

    private String baseUrl;

    private String authType;

    private String authConfig;

    private Integer state;

    private String createUser;

    private String modifyUser;

    @TableLogic
    private Integer isDelete;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
