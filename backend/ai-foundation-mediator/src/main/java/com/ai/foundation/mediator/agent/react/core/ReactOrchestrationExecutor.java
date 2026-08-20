package com.ai.foundation.mediator.agent.react.core;

import com.ai.foundation.com.constant.RunTypeConstant;
import com.ai.foundation.com.enums.RunStateEnum;
import com.ai.foundation.com.enums.RunStreamEventTypeEnum;
import com.ai.foundation.com.stream.RunStreamEnvelope;
import com.ai.foundation.mediator.agent.react.contract.AgentOrchestrationExecutor;
import com.ai.foundation.mediator.agent.react.contract.OrchestrationContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * ReAct 编排执行器。
 *
 * <p>把 {@link ReactAgentRunner#streamReactRun} 包成 {@link AgentOrchestrationExecutor} 契约：
 * <ul>
 *   <li>负责把 userMessage / systemPrompt 透传给 Runner。</li>
 *   <li>负责把 {@link OrchestrationContext#getCancelSignal()} 并入事件流，
 *       命中后发出 {@code RUN_CANCELLED} 终态。</li>
 *   <li>运行异常统一转为 {@code RUN_ERROR} 事件，避免在 onError 抛出导致 MedService
 *       没法把事件持久化到 {@code agent_run_event_log}。</li>
 * </ul>
 *
 * <p>注意：本类不持有 RunEventBus 直推逻辑。事件流仍由 MedService 通过
 * {@code doOnNext(persistEventAsync)} 落库 + SSE 推送给客户端。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReactOrchestrationExecutor implements AgentOrchestrationExecutor {

    private final ReactAgentRunner reactAgentRunner;

    @Override
    public String mode() {
        return RunTypeConstant.REACT;
    }

    @Override
    public Flux<RunStreamEnvelope> execute(OrchestrationContext context) {
        if (context == null || context.getRun() == null || context.getConversation() == null) {
            return Flux.error(new IllegalArgumentException("OrchestrationContext 必须包含 run / conversation"));
        }
        String runCode = context.getRun().getRunCode();
        String conversationCode = context.getConversation().getConversationCode();

        Flux<RunStreamEnvelope> modelStream = reactAgentRunner.streamReactRun(
                context.getRun(),
                context.getConversation(),
                context.getUserMessage(),
                context.getSystemPrompt(),
                context.getCancelSignal());

        // 取消分支：MedService 取消 Run 时通过 cancelSignal 触发，这里翻译为 RUN_CANCELLED 事件。
        Mono<RunStreamEnvelope> cancelMono = context.getCancelSignal() == null
                ? Mono.never()
                : context.getCancelSignal().asMono()
                        .map(v -> cancelledEnvelope(runCode, conversationCode));
        Flux<RunStreamEnvelope> cancelStream = cancelMono.flux();

        return Flux.merge(modelStream, cancelStream)
                .onErrorResume(err -> {
                    log.error("ReAct 编排异常 runCode={} conversationCode={}", runCode, conversationCode, err);
                    return Mono.just(errorEnvelope(runCode, conversationCode, err.getMessage()));
                });
    }

    private RunStreamEnvelope cancelledEnvelope(String runCode, String conversationCode) {
        RunStreamEnvelope env = new RunStreamEnvelope();
        env.setEventType(RunStreamEventTypeEnum.RUN_CANCELLED.getCode());
        env.setRunCode(runCode);
        env.setConversationCode(conversationCode);
        env.setTimestamp(System.currentTimeMillis());
        env.setTaskState(RunStateEnum.CANCELLED.getCode());
        env.setData("用户取消");
        return env;
    }

    private RunStreamEnvelope errorEnvelope(String runCode, String conversationCode, String message) {
        RunStreamEnvelope env = new RunStreamEnvelope();
        env.setEventType(RunStreamEventTypeEnum.RUN_ERROR.getCode());
        env.setRunCode(runCode);
        env.setConversationCode(conversationCode);
        env.setTimestamp(System.currentTimeMillis());
        env.setTaskState(RunStateEnum.FAILED.getCode());
        env.setData(message != null ? message : "ReAct 编排失败");
        return env;
    }
}
