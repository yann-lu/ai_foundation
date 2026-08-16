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
@TableName("agent_cli_command")
public class AgentCliCommand {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String commandPrefix;

    private String commandGroup;

    private String commandAction;

    private String commandName;

    private String cliTemplate;

    private String description;

    private String commandType;

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
