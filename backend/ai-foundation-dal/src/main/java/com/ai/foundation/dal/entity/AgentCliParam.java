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
@TableName("agent_cli_param")
public class AgentCliParam {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long cliId;

    private String paramName;

    private String paramFlag;

    private String paramType;

    private String itemType;

    private Integer isRequired;

    private String description;

    private String defaultValue;

    private Integer sortOrder;

    private String parentParamName;


    @TableLogic
    private Integer isDelete;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
