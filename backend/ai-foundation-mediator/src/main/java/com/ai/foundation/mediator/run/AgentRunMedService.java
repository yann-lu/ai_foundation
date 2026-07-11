package com.ai.foundation.mediator.run;

import com.ai.foundation.biz.conversation.AgentMessageService;
import com.ai.foundation.biz.run.AgentRunService;
import com.ai.foundation.com.constant.RunTypeConstant;
import com.ai.foundation.com.enums.RunStateEnum;
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
import reactor.core.scheduler.Schedulers;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentRunMedService {

    private final AgentRunService runService;
    private final AgentConversationMedService conversationMedService;
    private final AgentMessageService messageService;
    private final AgentOrchestrator orchestrator;
    private final RunEventBus runEventBus;

    /**
     * 创建 Run 并异步启动编排。
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
        String systemPromptFinal = trimmedSystemPrompt;
        AgentRun runFinal = run;

        org.springframework.core.task.SimpleAsyncTaskExecutor executor = new
                org.springframework.core.task.SimpleAsyncTaskExecutor("agent-run-");
        executor.submit(() -> {
            try {
                orchestrator.startRun(runFinal, conversation, trimmedMessage, systemPromptFinal);
            } catch (Exception e) {
                log.error("异步执行 Run 失败 runCode={}", runCode, e);
            }
        });

        log.info("createRun submitted runCode={} traceId={} conversationCode={}", runCode, traceId, conversationCode);
        return runCode;
    }

    /**
     * 订阅 Run SSE 事件流。
     *
     * @param runCode Run 编码
     * @return 事件流
     */
    public Flux<RunStreamEnvelope> streamRunEvents(String runCode) {
        if (StringUtils.isBlank(runCode)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "Run编码不能为空");
        }
        AgentRun run = runService.getByRunCode(runCode.trim());
        if (run == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "Run不存在");
        }
        return runEventBus.subscribe(runCode.trim())
                .subscribeOn(Schedulers.boundedElastic());
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
     * 取消 Run。
     *
     * @param runCode  Run 编码
     * @param operator 操作人
     */
    public void cancelRun(String runCode, String operator) {
        if (StringUtils.isBlank(runCode)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "Run编码不能为空");
        }
        AgentRun run = runService.getByRunCode(runCode.trim());
        if (run == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "Run不存在");
        }
        String state = run.getTaskState();
        if (RunStateEnum.COMPLETED.getCode().equals(state)
                || RunStateEnum.FAILED.getCode().equals(state)
                || RunStateEnum.CANCELLED.getCode().equals(state)) {
            throw new BusinessException(ResultCode.STATE_INVALID, "Run已结束，无法取消");
        }
        AgentConversationInfo conversation = conversationMedService.requireById(run.getConversationId());
        String conversationCode = conversation != null ? conversation.getConversationCode() : "";
        orchestrator.cancelRun(run, conversationCode);
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
}
