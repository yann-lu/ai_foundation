package com.ai.foundation.mediator.agent.react.contract;

import com.ai.foundation.dal.entity.AgentConversationInfo;
import com.ai.foundation.dal.entity.AgentRunInfo;
import com.ai.foundation.mediator.agent.context.AgentExecutionContext;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import reactor.core.publisher.Sinks;

import java.util.List;
import java.util.Map;

/**
 * 编排执行上下文：一次 Run 编排所需的全部入参。
 *
 * <p>由 {@link AgentOrchestrationExecutorRegistry} 选中的 Executor 消费。
 * 设计原则：
 * <ul>
 *   <li>编排层（{@code run/}）只负责构造此上下文，不再关心具体编排模式。</li>
 *   <li>具体 Executor（ReAct / Plan / Chat 等）只取自己关心的字段。</li>
 *   <li>{@code fullCatalog} / {@code planningCatalog} 由步骤 5 引入的 CapabilityRegistry 填充，
 *       当前可为空；保留字段位方便后续步骤扩展。</li>
 * </ul>
 */
@Getter
@Setter
@Builder
@ToString
public class OrchestrationContext {

    /** 已持久化的 Run。 */
    private AgentRunInfo run;

    /** 会话信息。 */
    private AgentConversationInfo conversation;

    /** 用户消息原文（已 trim）。 */
    private String userMessage;

    /** 系统提示词（已 trim，可为 null）。 */
    private String systemPrompt;

    /** 客户端 IP（用于审计/限流）。 */
    private String clientIp;

    /**
     * 取消信号：当 MedService 收到 /chat/runs/cancel 时通过此 Sinks 触发。
     * Executor 负责将其并入返回的 Flux，并在事件流中发出 RUN_CANCELLED 终态。
     */
    private Sinks.Empty<Void> cancelSignal;

    /** 执行上下文（traceId / project / product / accessToken 等）。可为空。 */
    private AgentExecutionContext executionContext;

    /**
     * 项目全量已授权能力目录。
     * <p>由步骤 5 的 {@code CapabilityRegistry.loadCatalog(productCode)} 填充；当前可为空。
     */
    private List<?> fullCatalog;

    /**
     * 本轮召回后的能力目录子集（用于 ReAct 装工具与 lookup_cli 兜底）。
     * <p>由步骤 6 的 {@code CapabilityRetriever.retrieveForReact(...)} 填充；当前可为空。
     */
    private List<?> planningCatalog;

    /**
     * 业务扩展参数（如 attachmentContext / extra）。由编排层透传给 Executor。
     */
    private Map<String, Object> extras;
}
