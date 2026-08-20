package com.ai.foundation.mediator.agent.react.contract;

import com.ai.foundation.com.constant.RunTypeConstant;
import com.ai.foundation.com.stream.RunStreamEnvelope;
import reactor.core.publisher.Flux;

/**
 * Agent 编排执行器契约。
 *
 * <p>每种编排模式（chat / plan / react / multi-agent / ...）实现本接口，由
 * {@link AgentOrchestrationExecutorRegistry} 按 {@link #mode()} 注册与查找。
 *
 * <h3>实现要点</h3>
 * <ul>
 *   <li>{@link #mode()} 必须返回 {@link RunTypeConstant} 中定义的常量之一。</li>
 *   <li>{@link #execute(OrchestrationContext)} 返回的 Flux 必须包含一个终态事件
 *       （RUN_COMPLETE / RUN_ERROR / RUN_CANCELLED），否则 MedService 的 takeUntil 不会终止。</li>
 *   <li>取消语义：实现类应消费 {@code context.getCancelSignal()}，命中后发出
 *       {@code RUN_CANCELLED} 事件并终止 Flux。</li>
 *   <li>执行异常应转为 {@code RUN_ERROR} 事件后正常结束，不要在 onError 中抛出，
 *       避免 MedService 没法做事件持久化。</li>
 * </ul>
 *
 * <h3>扩展方式</h3>
 * 新增模式时实现本接口 + 标注 {@code @Component}，无需修改任何编排层代码。
 */
public interface AgentOrchestrationExecutor {

    /**
     * 本执行器支持的编排模式，对应 {@link RunTypeConstant} 的常量。
     *
     * @return chat / plan / react / ...
     */
    String mode();

    /**
     * 执行一次 Run 编排。
     *
     * @param context 编排上下文（含 Run / Conversation / userMessage / systemPrompt / cancelSignal 等）
     * @return SSE 事件流，结束于终态事件
     */
    Flux<RunStreamEnvelope> execute(OrchestrationContext context);
}
