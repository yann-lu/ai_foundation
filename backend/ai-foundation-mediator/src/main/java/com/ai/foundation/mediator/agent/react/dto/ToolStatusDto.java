package com.ai.foundation.mediator.agent.react.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工具执行状态 DTO，配合 {@link com.ai.foundation.com.enums.RunStreamEventTypeEnum#TOOL_STATUS} SSE 事件使用。
 * 前端在工具卡片上根据 status 字段切换「执行中 / 成功 / 失败」展示。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolStatusDto {

    /** 工具名（与 ToolDefinition.name() 一致，例如 react_cli_xxx）。 */
    private String toolName;

    /** 状态：running / success / failed。 */
    private String status;

    /** 工具执行耗时（毫秒）；running 阶段为 null。 */
    private Long costMs;

    /** 失败时的错误描述；success 时为 null。 */
    private String errorMessage;

    /** 事件发生时间（毫秒），与 RunStreamEnvelope.timestamp 一致便于排序。 */
    private Long timestamp;
}
