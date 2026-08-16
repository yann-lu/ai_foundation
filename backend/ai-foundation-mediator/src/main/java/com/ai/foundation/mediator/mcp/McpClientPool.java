package com.ai.foundation.mediator.mcp;

import com.ai.foundation.biz.mcp.AgentMcpServerService;
import com.ai.foundation.com.exception.BusinessException;
import com.ai.foundation.com.response.ResultCode;
import com.ai.foundation.dal.entity.AgentMcpServer;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
@RequiredArgsConstructor
public class McpClientPool {

    private static final long STALE_TIMEOUT_MS = 10 * 60 * 1000L;

    private final AgentMcpServerService mcpServerService;
    private final ObjectMapper objectMapper;

    private final Map<Long, PooledClient> clientPool = new ConcurrentHashMap<>();

    public McpStdioClient getClient(Long mcpServerId) {
        if (mcpServerId == null) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "MCP 服务器ID为空");
        }
        AgentMcpServer server = mcpServerService.getByIdValid(mcpServerId);
        if (server == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "MCP 服务器不存在");
        }
        if (server.getState() != null && server.getState() == 0) {
            throw new BusinessException(ResultCode.STATE_INVALID, "MCP 服务器已停用");
        }
        return getOrCreate(server);
    }

    private McpStdioClient getOrCreate(AgentMcpServer server) {
        long id = server.getId();
        PooledClient pooled = clientPool.get(id);
        long now = System.currentTimeMillis();

        if (pooled != null) {
            if (pooled.client.isRunning()) {
                pooled.lastUsed.set(now);
                return pooled.client;
            } else {
                pooled.client.close();
                clientPool.remove(id);
            }
        }

        McpStdioClient client = createClient(server);
        client.start();
        PooledClient newPooled = new PooledClient(client, new AtomicLong(now));
        clientPool.put(id, newPooled);
        return client;
    }

    private McpStdioClient createClient(AgentMcpServer server) {
        String command = server.getCommand();
        String workingDir = server.getWorkingDir();
        Map<String, String> envMap = new HashMap<>();

        if (StringUtils.isNotBlank(server.getEnvVars())) {
            try {
                Map<String, String> parsed = objectMapper.readValue(
                        server.getEnvVars(),
                        new TypeReference<Map<String, String>>() {}
                );
                if (parsed != null) {
                    envMap.putAll(parsed);
                }
            } catch (Exception ex) {
                log.warn("Failed to parse MCP env_vars for server={}", server.getServerCode(), ex);
            }
        }

        return new McpStdioClient(command, workingDir, envMap);
    }

    public void invalidate(Long serverId) {
        if (serverId == null) return;
        PooledClient pooled = clientPool.remove(serverId);
        if (pooled != null) {
            pooled.client.close();
        }
    }

    @PreDestroy
    public void shutdown() {
        for (PooledClient pooled : clientPool.values()) {
            try {
                pooled.client.close();
            } catch (Exception ignored) {
            }
        }
        clientPool.clear();
        log.info("McpClientPool shutdown complete");
    }

    private static class PooledClient {
        final McpStdioClient client;
        final AtomicLong lastUsed;

        PooledClient(McpStdioClient client, AtomicLong lastUsed) {
            this.client = client;
            this.lastUsed = lastUsed;
        }
    }
}
