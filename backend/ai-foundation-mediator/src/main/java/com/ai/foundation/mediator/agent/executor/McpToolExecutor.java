package com.ai.foundation.mediator.agent.executor;

import com.ai.foundation.com.exception.BusinessException;
import com.ai.foundation.com.response.ResultCode;
import com.ai.foundation.dal.entity.AgentToolDefinition;
import com.ai.foundation.mediator.mcp.McpClientPool;
import com.ai.foundation.mediator.mcp.McpStdioClient;
import com.ai.foundation.mediator.mcp.McpToolCallResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class McpToolExecutor {

    private final McpClientPool mcpClientPool;

    public String execute(AgentToolDefinition tool, Map<String, Object> params) {
        if (tool == null) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "Tool 未配置");
        }
        if (tool.getMcpServerId() == null || tool.getMcpServerId() <= 0) {
            throw new BusinessException(ResultCode.PARAM_INVALID,
                    "MCP 工具未关联 mcp_server_id: toolId=" + tool.getId());
        }
        if (StringUtils.isBlank(tool.getMcpToolName())) {
            throw new BusinessException(ResultCode.PARAM_INVALID,
                    "MCP 工具未配置 mcp_tool_name: toolId=" + tool.getId());
        }

        long startMs = System.currentTimeMillis();
        log.info("McpToolExecutor request, toolId={}, mcpServerId={}, mcpTool={}, paramKeys={}",
                tool.getId(), tool.getMcpServerId(), tool.getMcpToolName(),
                params == null ? 0 : params.keySet());

        McpStdioClient client = mcpClientPool.getClient(tool.getMcpServerId());
        McpToolCallResult result = client.callTool(tool.getMcpToolName(), params);

        long costMs = System.currentTimeMillis() - startMs;
        if (!result.isSuccess()) {
            log.warn("McpToolExecutor failed, toolId={}, mcpTool={}, cost={}ms, err={}",
                    tool.getId(), tool.getMcpToolName(), costMs, result.getErrorMessage());
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE,
                    StringUtils.defaultIfBlank(result.getErrorMessage(), "MCP 工具调用失败"));
        }
        log.info("McpToolExecutor completed, toolId={}, mcpTool={}, cost={}ms, resultLen={}",
                tool.getId(), tool.getMcpToolName(), costMs,
                result.getContent() == null ? 0 : result.getContent().length());
        return StringUtils.defaultString(result.getContent());
    }
}
