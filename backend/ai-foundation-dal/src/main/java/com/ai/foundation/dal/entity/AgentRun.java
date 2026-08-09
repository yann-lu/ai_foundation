package com.ai.foundation.dal.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("agent_run")
public class AgentRun {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long conversationId;

    private String runCode;

    private String traceId;

    private String productCode;

    private Long messageId;

    private String runType;

    private String taskState;

    private Integer state;

    private Integer tokensPrompt;

    private Integer tokensCompletion;

    private BigDecimal cost;

    /** 本次 Run 实际发给模型的完整消息栈 JSON（system + 摘要 + 历史 + 用户消息）。 */
    private String requestMessages;

    /** 模型最终回复正文（已剥离思考链）。 */
    private String reply;

    /** 思考链内容（reasoning_content 或 &lt;think&gt; 解析出）。 */
    private String reasoning;

    @TableLogic
    private Integer isDelete;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
