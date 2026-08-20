package com.ai.foundation.mediator.run;

import com.ai.foundation.biz.conversation.AgentConversationService;
import com.ai.foundation.biz.conversation.AgentMessageService;
import com.ai.foundation.biz.run.AgentRunInfoService;
import com.ai.foundation.com.constant.RunTypeConstant;
import com.ai.foundation.com.enums.RunStateEnum;
import com.ai.foundation.com.enums.RunStreamEventTypeEnum;
import com.ai.foundation.com.exception.BusinessException;
import com.ai.foundation.com.response.ResultCode;
import com.ai.foundation.com.stream.RunStreamEnvelope;
import com.ai.foundation.com.trace.TraceUtils;
import com.ai.foundation.dal.entity.AgentConversationInfo;
import com.ai.foundation.dal.entity.AgentMessageInfo;
import com.ai.foundation.dal.entity.AgentRunInfo;
import com.ai.foundation.mediator.conversation.AgentConversationMedService;
import com.ai.foundation.biz.converter.RunConverter;
import com.ai.foundation.biz.run.AgentRunEventLogService;
import com.ai.foundation.biz.run.AgentRunTaskInfoService;
import com.ai.foundation.com.response.PageResult;
import com.ai.foundation.dal.entity.AgentRunEventLog;
import com.ai.foundation.dal.entity.AgentRunTaskInfo;
import com.ai.foundation.facade.dto.run.RequestMessageDTO;
import com.ai.foundation.facade.dto.run.RunDetailResponse;
import com.ai.foundation.facade.dto.run.RunEventDTO;
import com.ai.foundation.facade.dto.run.RunTaskDTO;
import com.ai.foundation.facade.dto.run.RunItemDTO;
import com.ai.foundation.mediator.agent.event.RunCancelFlagStore;
import com.ai.foundation.mediator.agent.react.contract.AgentOrchestrationExecutorRegistry;
import com.ai.foundation.mediator.agent.react.contract.OrchestrationContext;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentRunMedService {

    private final AgentRunInfoService runInfoService;
    private final AgentConversationMedService conversationMedService;
    private final AgentMessageService messageService;
    private final AgentOrchestrationExecutorRegistry orchestrationExecutorRegistry;
    private final RunCancelFlagStore runCancelFlagStore;
    private final AgentConversationService conversationService;
    private final AgentRunEventLogService runEventLogService;
    private final AgentRunTaskInfoService taskInfoService;
    private final RunConverter runConverter;
    private final ObjectMapper objectMapper;

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

        AgentRunInfo run = new AgentRunInfo();
        run.setRunCode(RunCodeGenerator.generate());
        run.setTraceId(traceId);
        run.setConversationId(conversation.getId());
        run.setMessageId(userMsg.getId());
        run.setProductCode(StringUtils.defaultIfBlank(conversation.getProductCode(), ""));
        run.setRunType(RunTypeConstant.REACT);
        run.setTaskState(RunStateEnum.CREATED.getCode());
        run.setState(0);
        runInfoService.save(run);

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
                AgentRunInfo run = runInfoService.getByRunCode(trimmedRunCode);
                if (run == null) {
                    throw new BusinessException(ResultCode.DATA_NOT_FOUND, "Run不存在");
                }
                PendingRun pending = pendingRuns.remove(trimmedRunCode);
                if (pending == null) {
                    throw new BusinessException(ResultCode.STATE_INVALID, "Run已启动或不存在");
                }
                updateRunState(pending.run(), pending.run().getRunType(), RunStateEnum.EXECUTING, 0);
                return pending;
            })
            .subscribeOn(Schedulers.boundedElastic())
            .flatMapMany(pending -> {
                OrchestrationContext ctx = OrchestrationContext.builder()
                        .run(pending.run())
                        .conversation(pending.conversation())
                        .userMessage(pending.userMessage())
                        .systemPrompt(pending.systemPrompt())
                        .cancelSignal(cancelSignal)
                        .build();
                Flux<RunStreamEnvelope> orchestrationStream =
                        orchestrationExecutorRegistry.getRequired(pending.run().getRunType()).execute(ctx);
                return orchestrationStream
                        .takeUntil(e -> isTerminal(e.getEventType()))
                        .concatWith(Mono.fromCallable(() -> envelopeRunComplete(
                                trimmedRunCode, pending.conversation().getConversationCode()))
                                .subscribeOn(Schedulers.boundedElastic()))
                        .doOnNext(env -> persistEventAsync(pending.run(), pending.conversation(), env));
            })
            .takeUntil(e -> isTerminal(e.getEventType()))
            .doFinally(signal -> {
                cancelSignals.remove(trimmedRunCode);
                runCancelFlagStore.clear(trimmedRunCode);
            });
    }

    private void persistEventAsync(AgentRunInfo run, AgentConversationInfo conversation, RunStreamEnvelope env) {
        try {
            String eventType = env.getEventType();
            // chat_token / chat_reasoning 由前端走 SSE 实时消费，无需入库；全量 token 流落库
            // 会让 event_log 在一次对话里膨胀到近千行，且事后回放价值低。
            // chat_reasoning 历史上有 emit 代码，8/17 后已无新数据，med 层做兜底过滤。
            if (RunStreamEventTypeEnum.CHAT_TOKEN.getCode().equals(eventType)
                    || RunStreamEventTypeEnum.CHAT_REASONING.getCode().equals(eventType)) {
                return;
            }

            String dataStr = null;
            if (RunStreamEventTypeEnum.RUN_COMPLETE.getCode().equals(eventType)) {
                // run_complete 不再冗余存 reply 全文；只存元信息。
                // 完整正文事实来源：agent_run_info.reply（已落库）/ SSE 实时 envelope.data。
                Map<String, Object> meta = new HashMap<>(1);
                meta.put("replyLen", run.getReply() != null ? run.getReply().length() : 0);
                dataStr = objectMapper.writeValueAsString(meta);
            } else if (env.getData() != null) {
                if (env.getData() instanceof String s) {
                    dataStr = s;
                } else {
                    dataStr = objectMapper.writeValueAsString(env.getData());
                }
                if (dataStr != null && dataStr.length() > 65535) {
                    dataStr = dataStr.substring(0, 65535);
                }
            }
            final String finalData = dataStr;
            Schedulers.boundedElastic().schedule(() ->
                    runEventLogService.appendEvent(
                            run.getId(),
                            conversation.getId(),
                            eventType,
                            env.getTaskState(),
                            finalData,
                            env.getTimestamp()
                    )
            );
        } catch (Exception ex) {
            log.warn("持久化事件日志失败 runId={} eventType={}", run.getId(), env.getEventType(), ex);
        }
    }

    /**
     * 查询 Run 详情。
     *
     * @param runCode Run 编码
     * @return Run 详情
     */
    public AgentRunInfo getRunDetailEntity(String runCode) {
        if (StringUtils.isBlank(runCode)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "Run编码不能为空");
        }
        AgentRunInfo run = runInfoService.getByRunCode(runCode.trim());
        if (run == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "Run不存在");
        }
        return run;
    }

    /**
     * 获取会话最近一条成功的 Run（用于刷新页面后回填 Inspector）。
     *
     * @param conversationCode 会话编码
     * @return 最近一条 Run，没有则返回 null
     */
    public com.baomidou.mybatisplus.core.metadata.IPage<AgentRunInfo> pageRuns(
            Long conversationId, long current, long size) {
        return runInfoService.pageByConversationId(conversationId, current, size);
    }

    public AgentRunInfo getLatestRunByConversation(String conversationCode) {
        if (StringUtils.isBlank(conversationCode)) {
            return null;
        }
        AgentConversationInfo conversation = conversationService.getByCode(conversationCode.trim());
        if (conversation == null) {
            return null;
        }
        return runInfoService.getLatestByConversationId(conversation.getId());
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
        AgentRunInfo run = runInfoService.getByRunCode(trimmedRunCode);
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
        // 同步写 Redis 协作标志，让 Spring AI Alibaba 框架层在下一轮 LLM 调用前真正停图。
        runCancelFlagStore.markCancelled(trimmedRunCode);
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
        AgentRunInfo run = runInfoService.getByRunCode(runCode.trim());
        if (run == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "Run不存在");
        }
        log.info("confirmRun runCode={} (骨架，无待确认任务)", runCode);
    }


    private static final TypeReference<List<RequestMessageDTO>> REQ_MSG_LIST_TYPE =
            new TypeReference<>() {};

    public RunDetailResponse getRunDetail(String runCode) {
        AgentRunInfo run = getRunDetailEntity(runCode);
        return buildRunDetailResponse(run);
    }

    public RunDetailResponse getLatestRunDetail(String conversationCode) {
        AgentRunInfo run = getLatestRunByConversation(conversationCode);
        if (run == null) {
            return null;
        }
        return buildRunDetailResponse(run);
    }

    public List<RunEventDTO> listRunEvents(String runCode) {
        AgentRunInfo run = getRunDetailEntity(runCode);
        List<AgentRunEventLog> events = runEventLogService.listByRunId(run.getId());
        return runConverter.toEventDtoList(events);
    }

    /**
     * 拉取指定会话下所有 Run 的事件日志（按 run_id asc + id asc 排序），
     * 供前端轨迹页拼接多轮对话。
     */
    public List<RunEventDTO> listRunEventsByConversation(String conversationCode) {
        if (conversationCode == null || conversationCode.isBlank()) {
            return List.of();
        }
        AgentConversationInfo conversation = conversationService.getByCode(conversationCode.trim());
        if (conversation == null) {
            return List.of();
        }
        List<AgentRunEventLog> events = runEventLogService.listByConversationId(conversation.getId());
        if (events.isEmpty()) {
            return List.of();
        }
        // 按 runId 一次性 join AgentRunInfo 取 runCode，避免 N+1
        java.util.Set<Long> runIds = new java.util.HashSet<>();
        for (AgentRunEventLog e : events) {
            if (e != null && e.getRunId() != null) {
                runIds.add(e.getRunId());
            }
        }
        java.util.Map<Long, String> runCodeMap = new java.util.HashMap<>();
        if (!runIds.isEmpty()) {
            List<AgentRunInfo> runs = runInfoService.lambdaQuery()
                    .in(AgentRunInfo::getId, runIds)
                    .list();
            for (AgentRunInfo r : runs) {
                if (r != null) {
                    runCodeMap.put(r.getId(), r.getRunCode());
                }
            }
        }
        List<RunEventDTO> dtos = runConverter.toEventDtoList(events);
        for (RunEventDTO dto : dtos) {
            if (dto != null && dto.getRunId() != null) {
                dto.setRunCode(runCodeMap.get(dto.getRunId()));
            }
        }
        return dtos;
    }

    public PageResult<RunItemDTO> pageRunItems(String conversationCode, long current, long size) {
        AgentConversationInfo conversation = conversationService.getByCode(conversationCode);
        if (conversation == null) {
            return new PageResult<>(List.of(), 0, current, size);
        }
        IPage<AgentRunInfo> page = pageRuns(conversation.getId(), current, size);
        List<RunItemDTO> records = runConverter.toItemDtoList(page.getRecords());
        return new PageResult<>(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    private RunDetailResponse buildRunDetailResponse(AgentRunInfo run) {
        AgentConversationInfo conversation = conversationMedService.requireById(run.getConversationId());
        RunDetailResponse response = new RunDetailResponse();
        response.setRunCode(run.getRunCode());
        response.setTraceId(run.getTraceId());
        response.setConversationCode(conversation.getConversationCode());
        response.setProductCode(run.getProductCode());
        response.setRunType(run.getRunType());
        response.setTaskState(run.getTaskState());
        response.setRequestMessages(parseRequestMessages(run.getRequestMessages()));
        response.setReply(run.getReply());
        response.setReasoning(run.getReasoning());
        response.setTasks(buildTaskDtos(run.getId()));
        return response;
    }

    private List<RunTaskDTO> buildTaskDtos(Long runId) {
        if (runId == null) {
            return List.of();
        }
        List<AgentRunTaskInfo> tasks = taskInfoService.listByRunId(runId);
        return runConverter.toTaskDtoList(tasks);
    }

    private List<RequestMessageDTO> parseRequestMessages(String json) {
        if (StringUtils.isBlank(json)) {
            return List.of();
        }
        try {
            List<RequestMessageDTO> list = objectMapper.readValue(json, REQ_MSG_LIST_TYPE);
            return list == null ? List.of() : list;
        } catch (JsonProcessingException e) {
            log.warn("解析 requestMessages 失败", e);
            return List.of();
        }
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

    private void updateRunState(AgentRunInfo run, String runType, RunStateEnum state, int compatState) {
        AgentRunInfo update = new AgentRunInfo();
        update.setId(run.getId());
        update.setRunType(runType);
        update.setTaskState(state.getCode());
        update.setState(compatState);
        update.setUpdateTime(LocalDateTime.now());
        runInfoService.updateById(update);
        run.setRunType(runType);
        run.setTaskState(state.getCode());
        run.setState(compatState);
    }

    private boolean isTerminal(String eventType) {
        return RunStreamEventTypeEnum.RUN_COMPLETE.getCode().equals(eventType)
                || RunStreamEventTypeEnum.RUN_ERROR.getCode().equals(eventType)
                || RunStreamEventTypeEnum.RUN_CANCELLED.getCode().equals(eventType);
    }

    private record PendingRun(AgentRunInfo run, AgentConversationInfo conversation,
                              String userMessage, String systemPrompt) {}
    private RunStreamEnvelope envelopeRunComplete(String runCode, String conversationCode) {
        RunStreamEnvelope env = new RunStreamEnvelope();
        env.setEventType(RunStreamEventTypeEnum.RUN_COMPLETE.getCode());
        env.setRunCode(runCode);
        env.setConversationCode(conversationCode);
        env.setTimestamp(System.currentTimeMillis());
        env.setTaskState(RunStateEnum.COMPLETED.getCode());
        try {
            AgentRunInfo run = runInfoService.getByRunCode(runCode);
            env.setData(run != null ? run.getReply() : "");
        } catch (Exception e) {
            env.setData("");
        }
        return env;
    }

}
