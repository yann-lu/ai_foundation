package com.ai.foundation.gateway.controller;

import com.ai.foundation.com.response.ApiResponse;
import com.ai.foundation.com.stream.RunStreamEnvelope;
import com.ai.foundation.dal.entity.AgentRun;
import com.ai.foundation.dal.entity.AgentConversationInfo;
import com.ai.foundation.facade.dto.run.CreateRunRequest;
import com.ai.foundation.facade.dto.run.CreateRunResponse;
import com.ai.foundation.facade.dto.run.RunCancelRequest;
import com.ai.foundation.facade.dto.run.RunDetailRequest;
import com.ai.foundation.facade.dto.run.RequestMessageDTO;
import com.ai.foundation.facade.dto.run.RunDetailResponse;
import com.ai.foundation.gateway.util.MonoUtils;
import com.ai.foundation.mediator.conversation.AgentConversationMedService;
import com.ai.foundation.mediator.run.AgentRunMedService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/chat/runs")
@RequiredArgsConstructor
public class RunController {

    private static final TypeReference<List<RequestMessageDTO>> REQ_MSG_LIST_TYPE =
            new TypeReference<>() {};

    private final AgentRunMedService runMedService;
    private final AgentConversationMedService conversationMedService;
    private final ObjectMapper objectMapper;

    @PostMapping("/create")
    public Mono<ApiResponse<CreateRunResponse>> create(
            @Valid @RequestBody CreateRunRequest request,
            ServerWebExchange exchange) {
        String clientIp = extractClientIp(exchange);
        return MonoUtils.fromBlocking(() -> {
            String runCode = runMedService.createRun(
                    request.getConversationCode(),
                    request.getUserMessage(),
                    request.getSystemPrompt(),
                    clientIp);
            return new CreateRunResponse(runCode, null);
        }).map(ApiResponse::success);
    }

    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<RunStreamEnvelope>> events(@RequestParam String runCode,
                                                           ServerWebExchange exchange) {
        exchange.getResponse().getHeaders().setCacheControl("no-cache");
        exchange.getResponse().getHeaders().set("X-Accel-Buffering", "no");
        return runMedService.streamRunEvents(runCode)
                .map(envelope -> ServerSentEvent.builder(envelope).build());
    }

    @PostMapping("/detail")
    public Mono<ApiResponse<RunDetailResponse>> detail(@Valid @RequestBody RunDetailRequest request) {
        return MonoUtils.fromBlocking(() -> {
            AgentRun run = runMedService.getRunDetail(request.getRunCode());
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
            return response;
        }).map(ApiResponse::success);
    }

    /**
     * 获取指定会话最近一条成功 Run 的详情（用于刷新页面后回填 Inspector）。
     */
    @GetMapping("/latest")
    public Mono<ApiResponse<RunDetailResponse>> latest(@RequestParam String conversationCode) {
        return MonoUtils.fromBlocking(() -> {
            AgentRun run = runMedService.getLatestRunByConversation(conversationCode);
            if (run == null) {
                return null;
            }
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
            return response;
        }).map(ApiResponse::success);
    }

    @PostMapping("/cancel")
    public Mono<ApiResponse<Boolean>> cancel(@Valid @RequestBody RunCancelRequest request) {
        return MonoUtils.fromBlocking(() -> {
            runMedService.cancelRun(request.getRunCode(), request.getOperator());
            return true;
        }).map(ApiResponse::success);
    }

    @PostMapping("/confirm")
    public Mono<ApiResponse<Boolean>> confirm(@Valid @RequestBody RunDetailRequest request) {
        return MonoUtils.fromBlocking(() -> {
            runMedService.confirmRun(request.getRunCode());
            return true;
        }).map(ApiResponse::success);
    }

    private List<RequestMessageDTO> parseRequestMessages(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            List<RequestMessageDTO> list =
                    objectMapper.readValue(json, REQ_MSG_LIST_TYPE);
            return list == null ? Collections.emptyList() : list;
        } catch (JsonProcessingException e) {
            log.warn("解析 requestMessages 失败", e);
            return Collections.emptyList();
        }
    }

    private String extractClientIp(ServerWebExchange exchange) {
        String ip = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (ip != null && !ip.isBlank()) {
            return ip.split(",")[0].trim();
        }
        return exchange.getRequest().getRemoteAddress() != null
                ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                : "";
    }
}
