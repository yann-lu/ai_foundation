package com.ai.foundation.mediator.agent.react.core;

import com.ai.foundation.com.enums.RunStreamEventTypeEnum;
import com.ai.foundation.com.stream.RunStreamEnvelope;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Getter
public class ReactStreamHandler {

    private final String runCode;
    private final String conversationCode;

    private final StringBuilder contentBuilder = new StringBuilder();
    private final StringBuilder reasoningBuilder = new StringBuilder();
    private final StringBuilder thoughtBuffer = new StringBuilder();

    private String finalReply = "";
    private boolean hasToolInvoked = false;

    public ReactStreamHandler(String runCode, String conversationCode) {
        this.runCode = runCode;
        this.conversationCode = conversationCode;
    }

    public List<RunStreamEnvelope> handle(NodeOutput nodeOutput) {
        List<RunStreamEnvelope> events = new ArrayList<>();
        if (!(nodeOutput instanceof StreamingOutput<?> streamingOutput)) {
            return events;
        }

        OutputType outputType = streamingOutput.getOutputType();
        if (outputType == null) {
            return events;
        }

        switch (outputType) {
            case AGENT_MODEL_STREAMING -> handleModelStreaming(streamingOutput, events);
            case AGENT_MODEL_FINISHED -> handleModelFinished(streamingOutput, events);
            case AGENT_TOOL_FINISHED -> handleToolFinished(streamingOutput, events);
            default -> {}
        }

        return events;
    }

    private void handleModelStreaming(StreamingOutput<?> output, List<RunStreamEnvelope> events) {
        String delta = output.chunk();
        if (StringUtils.isBlank(delta)) {
            return;
        }

        if (hasToolInvoked || contentBuilder.length() > 0) {
            contentBuilder.append(delta);
            events.add(envelope(RunStreamEventTypeEnum.CHAT_TOKEN, delta));
        } else {
            thoughtBuffer.append(delta);
            reasoningBuilder.append(delta);
            events.add(envelope(RunStreamEventTypeEnum.CHAT_REASONING, delta));
        }
    }

    private void handleModelFinished(StreamingOutput<?> output, List<RunStreamEnvelope> events) {
        Message msg = output.message();
        if (!(msg instanceof AssistantMessage assistant)) {
            return;
        }

        List<AssistantMessage.ToolCall> toolCalls = assistant.getToolCalls();
        if (toolCalls != null && !toolCalls.isEmpty()) {
            hasToolInvoked = true;
            for (AssistantMessage.ToolCall toolCall : toolCalls) {
                if (toolCall == null || StringUtils.isBlank(toolCall.name())) {
                    continue;
                }
                Map<String, Object> tc = new HashMap<>();
                tc.put("name", toolCall.name());
                tc.put("arguments", toolCall.arguments());
                events.add(envelope(RunStreamEventTypeEnum.TOOL_CALL, tc));
            }
            thoughtBuffer.setLength(0);
            contentBuilder.setLength(0);
            return;
        }

        String text = StringUtils.defaultIfBlank(assistant.getText(), contentBuilder.toString());
        if (StringUtils.isNotBlank(text)) {
            finalReply = text.trim();
        }
    }

    private void handleToolFinished(StreamingOutput<?> output, List<RunStreamEnvelope> events) {
        Message msg = output.message();
        if (msg instanceof ToolResponseMessage toolMsg) {
            Map<String, Object> toolResult = new HashMap<>();
            // Try to get tool name from the response
            String toolName = "tool_result";
            String resultText = "";
            // Spring AI ToolResponseMessage stores results in getResponses()
            if (toolMsg.getResponses() != null && !toolMsg.getResponses().isEmpty()) {
                var firstResponse = toolMsg.getResponses().get(0);
                if (firstResponse != null) {
                    // Try to get tool name
                    try {
                        var nameField = firstResponse.getClass().getMethod("getToolName");
                        var name = nameField.invoke(firstResponse);
                        if (name != null) toolName = name.toString();
                    } catch (Exception ignored) {}
                    // Try to get output/result
                    try {
                        var outputField = firstResponse.getClass().getMethod("getOutput");
                        var out = outputField.invoke(firstResponse);
                        if (out != null) resultText = out.toString();
                    } catch (Exception ignored) {}
                    // Fallback: toString the whole response
                    if (resultText.isBlank()) {
                        resultText = firstResponse.toString();
                    }
                }
            }
            // Fallback to getText() if responses didn't work
            if (resultText.isBlank() && StringUtils.isNotBlank(toolMsg.getText())) {
                resultText = toolMsg.getText();
            }
            toolResult.put("toolName", toolName);
            toolResult.put("result", StringUtils.abbreviate(resultText, 2000));
            events.add(envelope(RunStreamEventTypeEnum.TOOL_RESULT, toolResult));
        }
        contentBuilder.setLength(0);
    }

    public String getFinalReply() {
        return StringUtils.defaultIfBlank(finalReply, contentBuilder.toString()).trim();
    }

    public String getReasoning() {
        return reasoningBuilder.toString();
    }

    private RunStreamEnvelope envelope(RunStreamEventTypeEnum type, Object data) {
        RunStreamEnvelope env = new RunStreamEnvelope();
        env.setEventType(type.getCode());
        env.setRunCode(runCode);
        env.setConversationCode(conversationCode);
        env.setTimestamp(System.currentTimeMillis());
        env.setTaskState("executing");
        env.setData(data);
        return env;
    }
}
