package com.ai.foundation.mediator.agent.react.cli;

import com.ai.foundation.biz.run.AgentRunTaskInfoService;
import com.ai.foundation.com.constant.CliCommandTypeConstant;
import com.ai.foundation.com.exception.BusinessException;
import com.ai.foundation.com.response.ResultCode;
import com.ai.foundation.dal.entity.AgentCliCommand;
import com.ai.foundation.dal.entity.AgentRunTaskInfo;
import com.ai.foundation.mediator.agent.executor.CliCommandExecutor;
import com.ai.foundation.mediator.agent.executor.CliParamBindContext;
import com.ai.foundation.mediator.agent.react.core.ReactRunSession;
import com.ai.foundation.mediator.agent.react.dto.ReactCliToolInput;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReactCliToolInvoker {

    private final CliCommandExecutor cliCommandExecutor;
    private final AgentRunTaskInfoService taskInfoService;
    private final ObjectMapper objectMapper;

    private static final int MAX_RETRIES = 3;
    // 单次工具调用超时：MCP 类工具需要拉起子进程并渲染网页（playwright），耗时较长
    private static final long TOOL_TIMEOUT_SECONDS = 90;

    public String invoke(AgentCliCommand cli, ReactCliToolInput input) {
        ReactRunSession session = ReactRunSession.current();
        if (session == null) {
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "ReAct 会话未绑定");
        }
        if (cli == null || cli.getId() == null) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "CLI 能力缺失");
        }

        String toolName = ReactCliToolNames.resolveToolName(cli);
        String commandType = cli.getCommandType();
        if (!CliCommandTypeConstant.API.equals(commandType)
                && !CliCommandTypeConstant.MCP.equals(commandType)) {
            throw new BusinessException(ResultCode.PARAM_INVALID,
                    "ReAct 暂不支持命令类型: " + commandType);
        }

        Map<String, Object> toolInputParams = extractParams(input);
        CliParamBindContext bindContext = buildBindContext(session, cli);
        bindContext.setPrefilledParams(toolInputParams);

        Long taskId = null;
        long startMs = System.currentTimeMillis();
        try {
            AgentRunTaskInfo task = taskInfoService.createTask(
                    session.getRunId(),
                    "tool",
                    "API",
                    cli.getId(),
                    cli.getCommandName(),
                    session.getUserMessage()
            );
            taskId = task.getId();
            taskInfoService.markRunning(taskId);

            log.info("ReactCliToolInvoker begin, runCode={}, taskId={}, toolName={}, cliId={}",
                    session.getRunCode(), taskId, toolName, cli.getId());

            String result = invokeWithRetry(cli, bindContext, session, toolName, taskId);

            long costMs = System.currentTimeMillis() - startMs;
            String resultRef = StringUtils.abbreviate(result, 2000);
            taskInfoService.markSuccess(taskId, resultRef, costMs);

            session.markToolInvoked();
            session.getToolInterpretedResults().add(result);

            log.info("ReactCliToolInvoker completed, runCode={}, taskId={}, toolName={}, cost={}ms, resultLen={}",
                    session.getRunCode(), taskId, toolName, costMs,
                    result == null ? 0 : result.length());

            return StringUtils.defaultIfBlank(result, "(无返回内容)");
        } catch (Exception ex) {
            long costMs = System.currentTimeMillis() - startMs;
            if (taskId != null) {
                taskInfoService.markFailed(taskId,
                        ex.getMessage() != null ? StringUtils.abbreviate(ex.getMessage(), 500) : "unknown",
                        costMs);
            }
            log.error("ReactCliToolInvoker failed, runCode={}, taskId={}, toolName={}",
                    session.getRunCode(), taskId, toolName, ex);
            // 工具调用失败不再抛出异常，返回错误描述让 Agent 继续处理，避免整个对话卡死
            return "工具调用失败: " + toolName + " - " + (ex.getMessage() != null ? ex.getMessage() : "未知错误")
                    + "（已重试 " + MAX_RETRIES + " 次）";
        }
    }

    /**
     * 带超时和重试的工具调用
     */
    private String invokeWithRetry(AgentCliCommand cli, CliParamBindContext bindContext,
                                    ReactRunSession session, String toolName, Long taskId) throws Exception {
        Exception lastError = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                            if (CliCommandTypeConstant.MCP.equals(cli.getCommandType())) {
                                return cliCommandExecutor.executeMcp(
                                        cli.getId(),
                                        bindContext,
                                        session.getModelName()
                                );
                            }
                            return cliCommandExecutor.executeApi(
                                    cli.getId(),
                                    bindContext,
                                    session.getAccessToken(),
                                    session.getExecutionContext(),
                                    session.getModelName()
                            );
                        }
                );
                return future.get(TOOL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (TimeoutException ex) {
                lastError = new RuntimeException("调用超时（" + TOOL_TIMEOUT_SECONDS + "s）", ex);
                log.warn("ReactCliToolInvoker timeout, runCode={}, taskId={}, toolName={}, attempt={}/{}",
                        session.getRunCode(), taskId, toolName, attempt, MAX_RETRIES);
            } catch (ExecutionException ex) {
                lastError = (Exception) ex.getCause();
                log.warn("ReactCliToolInvoker error, runCode={}, taskId={}, toolName={}, attempt={}/{}",
                        session.getRunCode(), taskId, toolName, attempt, MAX_RETRIES, ex.getCause());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw ex;
            }
            // 如果不是最后一次尝试，等待一下再重试
            if (attempt < MAX_RETRIES) {
                Thread.sleep(1000L * attempt);
            }
        }
        throw lastError != null ? lastError : new RuntimeException("工具调用失败");
    }

    private CliParamBindContext buildBindContext(ReactRunSession session, AgentCliCommand cli) {
        CliParamBindContext bindContext = new CliParamBindContext();
        bindContext.setUserMessage(session.getUserMessage());
        bindContext.setInstruction(session.getUserMessage());
        bindContext.setCliCommandName(cli.getCommandName());
        bindContext.setPriorStepResults(session.getToolInterpretedResults());
        return bindContext;
    }

    private Map<String, Object> extractParams(ReactCliToolInput input) {
        Map<String, Object> result = new HashMap<>();
        if (input != null && input.getParams() != null) {
            result.putAll(input.getParams());
        }
        return result;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
