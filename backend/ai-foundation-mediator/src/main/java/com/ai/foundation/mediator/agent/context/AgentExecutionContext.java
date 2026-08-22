package com.ai.foundation.mediator.agent.context;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * 通用 Agent 平台运行时执行上下文。
 * <p>
 * 替代老项目 PMS 特供版本：去掉了 {@code hotelCode} / {@code blocCode} 等业务字段，
 * 所有租户隔离键、调用方身份、业务扩展统一进 {@link #contextVariables} 池。
 * <p>
 * 平台系统级字段（{@code todayDate} / {@code currentTime} / {@code currentTimezone}）
 * 通过 getter 现场计算，skill 激活后无条件追加到 body 末尾，业务方无需配置。
 * <p>
 * 会话编码 / Run 编码等内部 ID 字段已移除——不再进入 skill 提示词。
 */
@Data
public class AgentExecutionContext {

    /** 关联项目 ID，{@code agent_project.id} */
    private Long projectId;

    /** 项目编码（冗余，便于日志/链路） */
    private String projectCode;

    /** 调用方 access token（鉴权） */
    private String accessToken;

    /**
     * 通用 KV 池：调用方在 {@code ConversationCreateRequest.contextVariables} 传入的
     * 所有键值对。skill 模板里 {@code #{key}} 优先从这里取值。
     */
    private Map<String, Object> contextVariables = new HashMap<>();

    /** 今日日期（系统级，yyyy-MM-dd），getter 现场计算。 */
    public String getTodayDate() {
        return LocalDate.now().toString();
    }

    /** 当前时间（系统级，HH:mm:ss），getter 现场计算。 */
    public String getCurrentTime() {
        return LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
    }

    /** 系统时区（系统级，如 Asia/Shanghai），getter 现场计算。 */
    public String getCurrentTimezone() {
        return ZoneId.systemDefault().toString();
    }
}
