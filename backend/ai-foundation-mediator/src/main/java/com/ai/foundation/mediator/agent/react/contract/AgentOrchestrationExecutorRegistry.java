package com.ai.foundation.mediator.agent.react.contract;

import com.ai.foundation.com.exception.BusinessException;
import com.ai.foundation.com.response.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 编排执行器注册中心。
 *
 * <p>Spring 启动时收集所有 {@link AgentOrchestrationExecutor} Bean，按 {@code mode()} 索引到 Map。
 * 编排层通过 {@link #getRequired(String)} 取得对应模式执行器。
 *
 * <p>当两个 Executor 声明了相同的 mode 时，启动期会抛错（fail-fast），避免运行时静默错误。
 */
@Slf4j
@Component
public class AgentOrchestrationExecutorRegistry {

    private final Map<String, AgentOrchestrationExecutor> executors;

    public AgentOrchestrationExecutorRegistry(List<AgentOrchestrationExecutor> all) {
        Map<String, AgentOrchestrationExecutor> indexed = new LinkedHashMap<>();
        for (AgentOrchestrationExecutor executor : all) {
            String mode = executor.mode();
            if (mode == null || mode.isBlank()) {
                throw new IllegalStateException(
                        "AgentOrchestrationExecutor " + executor.getClass().getName()
                                + " 返回了空 mode()");
            }
            AgentOrchestrationExecutor prev = indexed.putIfAbsent(mode, executor);
            if (prev != null) {
                throw new IllegalStateException("AgentOrchestrationExecutor 重复注册 mode="
                        + mode + "：[" + prev.getClass().getName() + ", "
                        + executor.getClass().getName() + "]");
            }
            log.info("注册编排执行器 mode={} class={}", mode, executor.getClass().getSimpleName());
        }
        this.executors = Collections.unmodifiableMap(indexed);
    }

    /**
     * 按 mode 取得执行器；不存在时抛 {@link BusinessException}。
     *
     * @param mode RunTypeConstant 中的常量
     * @return 对应执行器
     */
    public AgentOrchestrationExecutor getRequired(String mode) {
        AgentOrchestrationExecutor executor = executors.get(mode);
        if (executor == null) {
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE,
                    "不支持的编排模式: " + mode
                            + "（已注册: " + executors.keySet() + "）");
        }
        return executor;
    }

    /**
     * 安全查询：未注册时返回 null。
     */
    public AgentOrchestrationExecutor find(String mode) {
        return executors.get(mode);
    }

    /**
     * 当前已注册的所有 mode（只读快照）。调试与文档生成用。
     */
    public Set<String> registeredModes() {
        return executors.keySet();
    }
}
