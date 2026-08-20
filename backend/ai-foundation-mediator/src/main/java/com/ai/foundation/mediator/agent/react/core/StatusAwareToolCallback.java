package com.ai.foundation.mediator.agent.react.core;

import com.ai.foundation.mediator.agent.react.stream.ReactToolStatusEmitter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 工具状态感知回调：在原始 {@link ToolCallback} 外面包一层，call 时按
 * running → (success | failed) 顺序通过 {@link ReactToolStatusEmitter} 推送
 * {@link com.ai.foundation.com.enums.RunStreamEventTypeEnum#TOOL_STATUS} SSE 事件。
 *
 * <p>解决：前端工具卡片在工具执行期间没有「执行中」状态信号，只能等
 * {@link com.ai.foundation.mediator.agent.react.core.ReactStreamHandler} 发出 TOOL_RESULT
 * 时才知道工具完成，期间一直灰着像卡死。
 *
 * <p>关键约束：ToolCallback.call 可能跑在 Reactor 线程，
 * 必须用 {@link ReactRunSession#callWithSession} 绑定 session，
 * 否则 emitter 在目标线程里拿不到 runCode / conversationCode。
 */
@Slf4j
public class StatusAwareToolCallback implements ToolCallback {

    /** {@link com.ai.foundation.mediator.agent.react.cli.ReactCliToolInvoker} 失败返回前缀。 */
    static final String INVOKER_FAIL_PREFIX = "工具调用失败:";

    private final ToolCallback delegate;
    private final ReactRunSession session;
    private final ReactToolStatusEmitter emitter;

    public StatusAwareToolCallback(ToolCallback delegate, ReactRunSession session,
                                    ReactToolStatusEmitter emitter) {
        this.delegate = delegate;
        this.session = session;
        this.emitter = emitter;
    }

    /**
     * 批量包装为带状态推送的回调。session 为 null 或列表为空时原样返回，避免对未挂 Run 的工具造成副作用。
     *
     * @param callbacks 原始回调列表
     * @param session   当前 Run 会话
     * @param emitter   状态推送器
     * @return 包装后列表
     */
    public static List<ToolCallback> wrapAll(List<ToolCallback> callbacks, ReactRunSession session,
                                              ReactToolStatusEmitter emitter) {
        if (CollectionUtils.isEmpty(callbacks)) {
            return List.of();
        }
        if (session == null || emitter == null) {
            log.warn("StatusAwareToolCallback.wrapAll skip wrap, session={} emitter={}", session, emitter);
            return callbacks;
        }
        List<ToolCallback> wrapped = new ArrayList<>(callbacks.size());
        for (ToolCallback callback : callbacks) {
            if (callback == null) {
                continue;
            }
            wrapped.add(new StatusAwareToolCallback(callback, session, emitter));
        }
        return wrapped;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public String call(String toolInput) {
        String toolName = getToolDefinition().name();
        return ReactRunSession.callWithSession(session, () -> {
            long startMs = System.currentTimeMillis();
            emitter.publishRunning(session, toolName);
            try {
                String result = delegate.call(toolInput);
                long costMs = System.currentTimeMillis() - startMs;
                if (isInvokerFailure(result)) {
                    emitter.publishFailed(session, toolName, result, costMs);
                } else {
                    emitter.publishSuccess(session, toolName, costMs);
                }
                return result;
            } catch (Exception ex) {
                long costMs = System.currentTimeMillis() - startMs;
                String msg = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
                emitter.publishFailed(session, toolName, msg, costMs);
                // 与 ReactCliToolInvoker 保持一致：失败不抛异常，返回错误文本让 ReAct 循环继续
                log.warn("StatusAwareToolCallback tool threw, runCode={} toolName={} costMs={} err={}",
                        session.getRunCode(), toolName, costMs, msg);
                return INVOKER_FAIL_PREFIX + " " + toolName + " - " + msg;
            }
        });
    }

    /**
     * 判断 delegate 返回值是否为 ReactCliToolInvoker 包装后的失败文本。
     * 这里只识别已知前缀，避免误判业务正常返回里恰好包含「工具调用失败」字样的内容。
     */
    private static boolean isInvokerFailure(String result) {
        return result != null && result.startsWith(INVOKER_FAIL_PREFIX);
    }
}
