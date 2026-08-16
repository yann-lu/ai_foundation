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

@Slf4j
@Component
@RequiredArgsConstructor
public class ReactCliToolInvoker {

    private final CliCommandExecutor cliCommandExecutor;
    private final AgentRunTaskInfoService taskInfoService;
    private final ObjectMapper objectMapper;

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
        if (!CliCommandTypeConstant.API.equals(commandType)) {
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

            String result = cliCommandExecutor.executeApi(
                    cli.getId(),
                    bindContext,
                    session.getAccessToken(),
                    session.getExecutionContext(),
                    session.getModelName()
            );

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
            throw ex;
        }
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
