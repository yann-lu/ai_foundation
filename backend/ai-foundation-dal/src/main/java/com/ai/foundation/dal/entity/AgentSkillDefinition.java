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
@TableName("agent_skill_definition")
public class AgentSkillDefinition {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String skillName;

    private String skillCode;

    private String description;

    private String skillType;

    private String systemPrompt;

    private String runtimeContextTemplate;

    private String configJson;

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
