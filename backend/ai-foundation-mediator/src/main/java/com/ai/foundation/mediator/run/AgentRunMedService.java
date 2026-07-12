package com.ai.foundation.mediator.run;

import com.ai.foundation.biz.conversation.AgentMessageService;
import com.ai.foundation.biz.run.AgentRunService;
import com.ai.foundation.com.constant.RunTypeConstant;
import com.ai.foundation.com.enums.RunStateEnum;
import com.ai.foundation.com.enums.RunStreamEventTypeEnum;
import com.ai.foundation.com.exception.BusinessException;
import com.ai.foundation.com.response.ResultCode;
import com.ai.foundation.com.stream.RunStreamEnvelope;
import com.ai.foundation.com.trace.TraceUtils;
import com.ai.foundation.dal.entity.AgentConversationInfo;
import com.ai.foundation.dal.entity.AgentMessageInfo;
import com.ai.foundation.dal.entity.AgentRun;
import com.ai.foundation.mediator.conversation.AgentConversationMedService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentRunMedService {

    private final AgentRunService runService;
    private final AgentConversationMedService conversationMedService;
    private final AgentMessageService messageService;
    private final AgentOrchestrator orchestrator;

    /** 待执行 Run 参数，createRun 时存入，streamRunEvents 订阅时取出启动。 */
    private final ConcurrentMap<String, PendingRun> pendingRuns = new ConcurrentHashMap<>();

    /** 每个 Run 的取消信号，cancelRun 时触发。 */
    private final ConcurrentMap<String, Sinks.Empty<Void>> cancelSignals = new ConcurrentHashMap<>();

    /**
     * 创建 Run 并保存待执行参数，编排器在客户端订阅事件流时启动。
     *
     * @param conversationCode 会话编码
     * @param userMessage      用户消息
     * @param systemPrompt     系统提示词（可选）
     * @param clientIp         客户端 IP
     * @return Run 编码
     */
    public String createRun(String conversationCode, String userMessage,
                            String systemPrompt, String clientIp) {
        if (StringUtils.isBlank(conversationCode)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "会话编码不能为空");
        }
        if (StringUtils.isBlank(userMessage)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "用户消息不能为空");
        }

        AgentConversationInfo conversation = conversationMedService.requireByCode(conversationCode.trim());
        String trimmedMessage = userMessage.trim();
        String trimmedSystemPrompt = systemPrompt != null ? systemPrompt.trim() : null;

        AgentMessageInfo userMsg = saveUserMessage(conversation.getId(), trimmedMessage, clientIp);
        String traceId = TraceUtils.newTraceId();

        AgentRun run = new AgentRun();
        run.setRunCode(RunCodeGenerator.generate());
        run.setTraceId(traceId);
        run.setConversationId(conversation.getId());
        run.setMessageId(userMsg.getId());
        run.setProductCode(StringUtils.defaultIfBlank(conversation.getProductCode(), ""));
        run.setRunType(RunTypeConstant.CHAT);
        run.setTaskState(RunStateEnum.CREATED.getCode());
        run.setState(0);
        runService.save(run);

        String runCode = run.getRunCode();
        pendingRuns.put(runCode, new PendingRun(run, conversation, trimmedMessage, trimmedSystemPrompt));

        log.info("createRun saved runCode={} traceId={} conversationCode={} (awaiting SSE subscription)",
                runCode, traceId, conversationCode);
        return runCode;
    }

    /**
     * 订阅 Run SSE 事件流。首次订阅时启动编排器，模型 token 逐条推送到 SSE。
     *
     * @param runCode Run 编码
     * @return 事件流
     */
    public Flux<RunStreamEnvelope> streamRunEvents(String runCode) {
        if (StringUtils.isBlank(runCode)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "Run编码不能为空");
        }
        String trimmedRunCode = runCode.trim();

        Sinks.Empty<Void> cancelSignal = Sinks.empty();
        cancelSignals.put(trimmedRunCode, cancelSignal);

        return Mono.fromCallable(() -> {
                AgentRun run = runService.getByRunCode(trimmedRunCode);
                if (run == null) {
                    throw new BusinessException(ResultCode.DATA_NOT_FOUND, "Run不存在");
                }
                PendingRun pending = pendingRuns.remove(trimmedRunCode);
                if (pending == null) {
                    throw new BusinessException(ResultCode.STATE_INVALID, "Run已启动或不存在");
                }
                updateRunState(pending.run(), RunTypeConstant.CHAT, RunStateEnum.EXECUTING, 0);
                return pending;
            })
            .subscribeOn(Schedulers.boundedElastic())
            .flatMapMany(pending -> {
                String rc = pending.run().getRunCode();
                String cc = pending.conversation().getConversationCode();
                return Flux.merge(
                    orchestrator.streamRun(pending.run(), pending.conversation(),
                            pending.userMessage(), pending.systemPrompt()),
                    cancelSignal.asMono()
                        .map(v -> cancelledEnvelope(rc, cc))
                        .cast(RunStreamEnvelope.class)
                ).takeUntil(e -> isTerminal(e.getEventType()));
            })
            .doFinally(signal -> cancelSignals.remove(trimmedRunCode));
    }

    /**
     * 查询 Run 详情。
     *
     * @param runCode Run 编码
     * @return Run 详情
     */
    public AgentRun getRunDetail(String runCode) {
        if (StringUtils.isBlank(runCode)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "Run编码不能为空");
        }
        AgentRun run = runService.getByRunCode(runCode.trim());
        if (run == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "Run不存在");
        }
        return run;
    }

    /**
     * 取消 Run：触发取消信号并更新状态。
     *
     * @param runCode  Run 编码
     * @param operator 操作人
     */
    public void cancelRun(String runCode, String operator) {
        if (StringUtils.isBlank(runCode)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "Run编码不能为空");
        }
        String trimmedRunCode = runCode.trim();
        AgentRun run = runService.getByRunCode(trimmedRunCode);
        if (run == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "Run不存在");
        }
        String state = run.getTaskState();
        if (RunStateEnum.COMPLETED.getCode().equals(state)
                || RunStateEnum.FAILED.getCode().equals(state)
                || RunStateEnum.CANCELLED.getCode().equals(state)) {
            throw new BusinessException(ResultCode.STATE_INVALID, "Run已结束，无法取消");
        }
        Sinks.Empty<Void> signal = cancelSignals.get(trimmedRunCode);
        if (signal != null) {
            signal.tryEmitEmpty();
        }
        log.info("cancelRun runCode={} operator={}", runCode, operator);
    }

    /**
     * 确认 Run（骨架，当前直接返回，无待确认任务）。
     *
     * @param runCode Run 编码
     */
    public void confirmRun(String runCode) {
        if (StringUtils.isBlank(runCode)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "Run编码不能为空");
        }
        AgentRun run = runService.getByRunCode(runCode.trim());
        if (run == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "Run不存在");
        }
        log.info("confirmRun runCode={} (骨架，无待确认任务)", runCode);
    }

    // ========================= 内部方法 =========================

    private AgentMessageInfo saveUserMessage(Long conversationId, String content, String clientIp) {
        AgentMessageInfo msg = new AgentMessageInfo();
        msg.setConversationId(conversationId);
        msg.setRole("user");
        msg.setContent(content);
        msg.setClientIp(clientIp != null ? clientIp : "");
        msg.setState(1);
        messageService.save(msg);
        return msg;
    }

    private void updateRunState(AgentRun run, String runType, RunStateEnum state, int compatState) {
        AgentRun update = new AgentRun();
        update.setId(run.getId());
        update.setRunType(runType);
        update.setTaskState(state.getCode());
        update.setState(compatState);
        update.setUpdateTime(LocalDateTime.now());
        runService.updateById(update);
        run.setRunType(runType);
        run.setTaskState(state.getCode());
        run.setState(compatState);
    }

    private RunStreamEnvelope cancelledEnvelope(String runCode, String conversationCode) {
        RunStreamEnvelope env = new RunStreamEnvelope();
        env.setEventType(RunStreamEventTypeEnum.RUN_CANCELLED.getCode());
        env.setRunCode(runCode);
        env.setConversationCode(conversationCode);
        env.setTimestamp(System.currentTimeMillis());
        env.setTaskState(RunStateEnum.CANCELLED.getCode());
        env.setData(null);
        return env;
    }

    private boolean isTerminal(String eventType) {
        return RunStreamEventTypeEnum.RUN_COMPLETE.getCode().equals(eventType)
                || RunStreamEventTypeEnum.RUN_ERROR.getCode().equals(eventType)
                || RunStreamEventTypeEnum.RUN_CANCELLED.getCode().equals(eventType);
    }

    private record PendingRun(AgentRun run, AgentConversationInfo conversation,
                              String userMessage, String systemPrompt) {}
}
