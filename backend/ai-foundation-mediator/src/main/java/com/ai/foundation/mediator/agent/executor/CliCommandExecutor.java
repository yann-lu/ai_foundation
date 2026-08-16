package com.ai.foundation.mediator.agent.executor;

import com.ai.foundation.biz.cli.AgentCliCommandService;
import com.ai.foundation.biz.cli.AgentCliParamService;
import com.ai.foundation.biz.cli.AgentToolDefinitionService;
import com.ai.foundation.com.constant.CliCommandTypeConstant;
import com.ai.foundation.com.exception.BusinessException;
import com.ai.foundation.com.response.ResultCode;
import com.ai.foundation.dal.entity.AgentCliCommand;
import com.ai.foundation.dal.entity.AgentCliParam;
import com.ai.foundation.dal.entity.AgentToolDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class CliCommandExecutor {

    private final AgentCliCommandService cliCommandService;
    private final AgentCliParamService cliParamService;
    private final AgentToolDefinitionService toolDefinitionService;
    private final CliParamBinder cliParamBinder;
    private final ApiToolExecutor apiToolExecutor;
    private final McpToolExecutor mcpToolExecutor;

    public String executeApi(Long cliId, CliParamBindContext bindContext, String accessToken,
                             com.ai.foundation.mediator.agent.context.AgentExecutionContext context,
                             String modelName) {
        AgentCliCommand cli = requireCli(cliId);
        if (!CliCommandTypeConstant.API.equals(cli.getCommandType())) {
            throw new BusinessException(ResultCode.PARAM_INVALID,
                    "CLI 类型不匹配 API: " + cli.getCommandName());
        }
        AgentToolDefinition tool = toolDefinitionService.getByCliId(cliId);
        if (tool == null) {
            throw new BusinessException(ResultCode.PARAM_INVALID,
                    "CLI 未配置 Tool: cliId=" + cliId);
        }
        List<AgentCliParam> params = cliParamService.listByCliId(cliId);
        if (bindContext != null) {
            bindContext.setCliCommandName(cli.getCommandName());
            enrichBindContext(bindContext, tool);
        }
        Map<String, Object> boundParams = cliParamBinder.bind(bindContext, params, modelName);
        log.info("CliCommandExecutor api, cliId={}, commandName={}, paramKeys={}",
                cliId, cli.getCommandName(), boundParams.keySet());

        long startMs = System.currentTimeMillis();
        try {
            String result = apiToolExecutor.execute(tool, boundParams, accessToken);
            log.info("CliCommandExecutor api completed, cliId={}, cost={}ms", cliId, System.currentTimeMillis() - startMs);
            return result;
        } catch (Exception ex) {
            log.error("CliCommandExecutor api failed, cliId={}, commandName={}",
                    cliId, cli.getCommandName(), ex);
            throw ex;
        }
    }

    public String executeMcp(Long cliId, CliParamBindContext bindContext, String modelName) {
        AgentCliCommand cli = requireCli(cliId);
        if (!CliCommandTypeConstant.MCP.equals(cli.getCommandType())) {
            throw new BusinessException(ResultCode.PARAM_INVALID,
                    "CLI 类型不匹配 MCP: " + cli.getCommandName());
        }
        AgentToolDefinition tool = toolDefinitionService.getByCliId(cliId);
        if (tool == null) {
            throw new BusinessException(ResultCode.PARAM_INVALID,
                    "CLI 未配置 Tool: cliId=" + cliId);
        }
        List<AgentCliParam> params = cliParamService.listByCliId(cliId);
        if (bindContext != null) {
            bindContext.setCliCommandName(cli.getCommandName());
        }
        Map<String, Object> boundParams = cliParamBinder.bind(bindContext, params, modelName);
        log.info("CliCommandExecutor mcp, cliId={}, commandName={}, mcpTool={}, paramKeys={}",
                cliId, cli.getCommandName(), tool.getMcpToolName(), boundParams.keySet());

        long startMs = System.currentTimeMillis();
        try {
            String result = mcpToolExecutor.execute(tool, boundParams);
            log.info("CliCommandExecutor mcp completed, cliId={}, cost={}ms", cliId, System.currentTimeMillis() - startMs);
            return result;
        } catch (Exception ex) {
            log.error("CliCommandExecutor mcp failed, cliId={}, commandName={}",
                    cliId, cli.getCommandName(), ex);
            throw ex;
        }
    }

    private AgentCliCommand requireCli(Long cliId) {
        AgentCliCommand cli = cliCommandService.getById(cliId);
        if (cli == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "CLI 不存在: id=" + cliId);
        }
        if (cli.getCommandType() == null || cli.getCommandType().isBlank()) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "CLI commandType 未配置");
        }
        return cli;
    }

    private void enrichBindContext(CliParamBindContext bindContext, AgentToolDefinition tool) {
        if (bindContext == null) {
            return;
        }
        if (bindContext.getRequestSchema() == null || bindContext.getRequestSchema().isBlank()) {
            bindContext.setRequestSchema(tool.getRequestSchema());
        }
        if (bindContext.getResponseSchema() == null || bindContext.getResponseSchema().isBlank()) {
            bindContext.setResponseSchema(tool.getResponseSchema());
        }
    }
}
