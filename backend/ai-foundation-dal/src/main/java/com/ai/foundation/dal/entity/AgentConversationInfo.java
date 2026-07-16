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
@TableName("agent_conversation_info")
public class AgentConversationInfo {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    private String productCode;

    private String conversationCode;

    private Long userId;

    private String contextVariables;

    private String title;

    private String summary;

    private String modelProvider;

    private String modelName;

    private Integer isPin;

    private LocalDateTime lastMessageTime;

    private Integer state;

    @TableLogic
    private Integer isDelete;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
