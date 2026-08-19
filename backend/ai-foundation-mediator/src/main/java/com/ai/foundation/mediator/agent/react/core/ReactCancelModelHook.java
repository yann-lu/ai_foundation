package com.ai.foundation.mediator.agent.react.core;

import com.ai.foundation.mediator.agent.event.RunCancelFlagStore;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.InterruptableAction;
import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;
import com.alibaba.cloud.ai.graph.agent.hook.ModelHook;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * ReAct 取消 Hook。
 *
 * <p>同时继承 Spring AI Alibaba 的 {@link ModelHook} 与实现 {@link InterruptableAction}：
 * <ul>
 *   <li>{@code beforeModel} 故意不修改状态，所有取消判定集中在 {@code interrupt}。</li>
 *   <li>{@code interrupt} 每轮 LLM 调用前由框架回调；命中取消标志时返回
 *       {@link InterruptionMetadata}，框架据此停掉整个 ReAct 图。</li>
 * </ul>
 *
 * <p>与现有 {@code Sinks.Empty<Void>} 取消信号是「双保险」关系：Sinks 切外层 Flux 切 SSE；
 * 本 Hook 切框架内层图，让模型已经发出的 token 真正停算。
 */
@Slf4j
public class ReactCancelModelHook extends ModelHook implements InterruptableAction {

    private static final String HOOK_NODE_NAME = "react_cancel_hook";

    private final RunCancelFlagStore runCancelFlagStore;

    private final String runCode;

    public ReactCancelModelHook(RunCancelFlagStore runCancelFlagStore, String runCode) {
        this.runCancelFlagStore = runCancelFlagStore;
        this.runCode = runCode;
    }

    @Override
    public String getName() {
        return HOOK_NODE_NAME;
    }

    @Override
    public CompletableFuture<Map<String, Object>> beforeModel(OverAllState state, RunnableConfig config) {
        // beforeModel 不改 state；实际终止由框架回调 interrupt() 完成。
        return CompletableFuture.completedFuture(Collections.emptyMap());
    }

    @Override
    public Optional<InterruptionMetadata> interrupt(String nodeId, OverAllState state, RunnableConfig config) {
        if (StringUtils.isBlank(runCode) || runCancelFlagStore == null) {
            return Optional.empty();
        }
        if (!runCancelFlagStore.isCancelled(runCode)) {
            return Optional.empty();
        }
        log.info("ReactCancelModelHook interrupt fired, runCode={}, nodeId={}", runCode, nodeId);
        return Optional.of(InterruptionMetadata.builder(nodeId, state)
                .addMetadata("cancelled_by_user", Boolean.TRUE)
                .addMetadata("run_code", runCode)
                .build());
    }
}
