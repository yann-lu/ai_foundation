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
    /** 主键 */
    private Long id;

    /** 关联项目ID，agent_project.id */
    private Long projectId;

    /** 产品编码，冗余字段，便于按产品查询 */
    private String productCode;

    /** 会话对外编码 */
    private String conversationCode;

    /** 用户ID */
    private Long userId;

    /** 会话上下文变量JSON，创建会话时按项目变量定义固化 */
    private String contextVariables;

    /** 对话标题 */
    private String title;

    /** 会话滚动摘要，用于压缩长期历史 */
    private String summary;

    /** 模型提供商 */
    private String modelProvider;

    /** 模型名称 */
    private String modelName;

    /** 是否置顶：0-否，1-是 */
    private Integer isPin;

    /** 最后消息时间 */
    private LocalDateTime lastMessageTime;

    /** 状态：0-活跃，1-归档 */
    private Integer state;

    @TableLogic
    /** 软删：0-未删，1-已删 */
    private Integer isDelete;

    @TableField(fill = FieldFill.INSERT)
    /** 创建时间 */
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    /** 更新时间 */
    private LocalDateTime updateTime;
}
