package com.ai.foundation.mediator.mcp;

import com.ai.foundation.com.exception.BusinessException;
import com.ai.foundation.com.response.ResultCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public class McpStdioClient implements AutoCloseable {

    private static final long DEFAULT_TIMEOUT_SECONDS = 60;
    private static final long INIT_TIMEOUT_SECONDS = 60;

    private final String command;
    private final String workingDir;
    private final Map<String, String> envVars;
    private final ObjectMapper objectMapper;

    private Process process;
    private BufferedWriter stdinWriter;
    private BufferedReader stdoutReader;
    private Thread readThread;
    private volatile boolean running = false;

    private final AtomicLong requestId = new AtomicLong(0);
    private final Map<Long, CompletableFuture<JsonNode>> pendingRequests = new ConcurrentHashMap<>();

    public McpStdioClient(String command, String workingDir, Map<String, String> envVars) {
        this.command = command;
        this.workingDir = workingDir;
        this.envVars = envVars;
        this.objectMapper = new ObjectMapper();
    }

    public void start() {
        if (running) {
            return;
        }
        try {
            ProcessBuilder pb = new ProcessBuilder(command.split("\\s+"));
            if (StringUtils.isNotBlank(workingDir)) {
                pb.directory(new java.io.File(workingDir));
            }
            if (envVars != null && !envVars.isEmpty()) {
                pb.environment().putAll(envVars);
            }
            pb.redirectErrorStream(false);

            process = pb.start();
            stdinWriter = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
            stdoutReader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));

            running = true;

            readThread = new Thread(this::readLoop, "mcp-stdio-reader-" + command);
            readThread.setDaemon(true);
            readThread.start();

            Thread stderrThread = new Thread(this::readStderr, "mcp-stderr-reader-" + command);
            stderrThread.setDaemon(true);
            stderrThread.start();

            initialize();

            log.info("McpStdioClient started, command={}", command);
        } catch (Exception ex) {
            log.error("McpStdioClient start failed, command={}", command, ex);
            close();
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE,
                    "MCP 服务器启动失败: " + (ex.getMessage() != null ? ex.getMessage() : "unknown"));
        }
    }

    private void initialize() throws Exception {
        ObjectNode params = objectMapper.createObjectNode();
        ObjectNode protocolVersion = objectMapper.createObjectNode();
        protocolVersion.put("protocolVersion", "2024-11-05");
        ObjectNode capabilities = objectMapper.createObjectNode();
        capabilities.set("capabilities", objectMapper.createObjectNode());
        ObjectNode clientInfo = objectMapper.createObjectNode();
        clientInfo.put("name", "ai-foundation-mcp-client");
        clientInfo.put("version", "1.0.0");
        params.set("clientInfo", clientInfo);
        params.put("protocolVersion", "2024-11-05");
        params.set("capabilities", objectMapper.createObjectNode());

        JsonNode response = sendRequest("initialize", params, INIT_TIMEOUT_SECONDS);
        log.debug("MCP initialize response: {}", response);
        sendNotification("notifications/initialized", null);
    }

    public McpToolCallResult callTool(String toolName, Map<String, Object> arguments) {
        if (!running) {
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "MCP 客户端未启动");
        }
        try {
            ObjectNode params = objectMapper.createObjectNode();
            params.put("name", toolName);
            ObjectNode argsNode = objectMapper.createObjectNode();
            if (arguments != null) {
                for (Map.Entry<String, Object> entry : arguments.entrySet()) {
                    argsNode.set(entry.getKey(), objectMapper.valueToTree(entry.getValue()));
                }
            }
            params.set("arguments", argsNode);

            JsonNode response = sendRequest("tools/call", params, DEFAULT_TIMEOUT_SECONDS);
            return parseToolResult(response);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("MCP tool call failed, tool={}", toolName, ex);
            return McpToolCallResult.failure("工具调用失败: " + (ex.getMessage() != null ? ex.getMessage() : "unknown"));
        }
    }

    private McpToolCallResult parseToolResult(JsonNode response) {
        if (response == null) {
            return McpToolCallResult.failure("空响应");
        }
        JsonNode content = response.get("content");
        if (content == null || !content.isArray() || content.isEmpty()) {
            JsonNode error = response.get("error");
            if (error != null) {
                return McpToolCallResult.failure(error.asText());
            }
            boolean isError = response.has("isError") && response.get("isError").asBoolean(false);
            if (isError) {
                return McpToolCallResult.failure("工具执行错误");
            }
            return McpToolCallResult.success("");
        }

        StringBuilder sb = new StringBuilder();
        for (JsonNode item : content) {
            String type = item.path("type").asText("");
            if ("text".equals(type)) {
                if (sb.length() > 0) {
                    sb.append("\n");
                }
                sb.append(item.path("text").asText(""));
            }
        }
        boolean isError = response.has("isError") && response.get("isError").asBoolean(false);
        if (isError) {
            return McpToolCallResult.failure(sb.toString());
        }
        return McpToolCallResult.success(sb.toString());
    }

    private JsonNode sendRequest(String method, JsonNode params, long timeoutSeconds) throws Exception {
        long id = requestId.incrementAndGet();
        ObjectNode request = objectMapper.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", id);
        request.put("method", method);
        if (params != null) {
            request.set("params", params);
        }

        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        pendingRequests.put(id, future);

        try {
            String json = objectMapper.writeValueAsString(request);
            log.debug("MCP -> stdin: {}", json);
            synchronized (stdinWriter) {
                stdinWriter.write(json);
                stdinWriter.newLine();
                stdinWriter.flush();
            }

            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException ex) {
            pendingRequests.remove(id);
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE,
                    "MCP 请求超时(" + timeoutSeconds + "s): " + method);
        } catch (Exception ex) {
            pendingRequests.remove(id);
            throw ex;
        }
    }

    private void sendNotification(String method, JsonNode params) throws IOException {
        ObjectNode notification = objectMapper.createObjectNode();
        notification.put("jsonrpc", "2.0");
        notification.put("method", method);
        if (params != null) {
            notification.set("params", params);
        }
        String json = objectMapper.writeValueAsString(notification);
        synchronized (stdinWriter) {
            stdinWriter.write(json);
            stdinWriter.newLine();
            stdinWriter.flush();
        }
    }

    private void readLoop() {
        try {
            String line;
            while (running && (line = stdoutReader.readLine()) != null) {
                if (StringUtils.isBlank(line)) {
                    continue;
                }
                log.debug("MCP <- stdout: {}", line);
                handleMessage(line);
            }
        } catch (IOException ex) {
            if (running) {
                log.warn("MCP stdout read error", ex);
            }
        } finally {
            running = false;
            for (CompletableFuture<JsonNode> future : pendingRequests.values()) {
                future.completeExceptionally(new IOException("MCP connection closed"));
            }
            pendingRequests.clear();
        }
    }

    private void readStderr() {
        try (BufferedReader errReader = new BufferedReader(
                new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = errReader.readLine()) != null) {
                log.debug("MCP stderr: {}", line);
            }
        } catch (IOException ex) {
            if (running) {
                log.debug("MCP stderr read ended: {}", ex.getMessage());
            }
        }
    }

    private void handleMessage(String line) {
        try {
            JsonNode msg = objectMapper.readTree(line);
            JsonNode idNode = msg.get("id");
            JsonNode errorNode = msg.get("error");
            JsonNode resultNode = msg.get("result");

            if (idNode != null && idNode.isNumber()) {
                long id = idNode.asLong();
                CompletableFuture<JsonNode> future = pendingRequests.remove(id);
                if (future != null) {
                    if (errorNode != null) {
                        String errMsg = errorNode.path("message").asText("unknown error");
                        future.completeExceptionally(new RuntimeException("MCP error: " + errMsg));
                    } else {
                        future.complete(resultNode);
                    }
                }
            } else {
                log.debug("MCP notification: {}", line);
            }
        } catch (Exception ex) {
            log.warn("Failed to parse MCP message: {}", line, ex);
        }
    }

    @Override
    public void close() {
        running = false;
        for (CompletableFuture<JsonNode> future : pendingRequests.values()) {
            future.completeExceptionally(new IOException("MCP client closed"));
        }
        pendingRequests.clear();

        if (stdinWriter != null) {
            try {
                stdinWriter.close();
            } catch (IOException ignored) {
            }
        }
        if (stdoutReader != null) {
            try {
                stdoutReader.close();
            } catch (IOException ignored) {
            }
        }
        if (process != null) {
            process.destroy();
            try {
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            process = null;
        }
        log.info("McpStdioClient closed, command={}", command);
    }

    public boolean isRunning() {
        return running && process != null && process.isAlive();
    }
}
