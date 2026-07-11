package com.ai.foundation.gateway.controller;

import com.ai.foundation.com.response.ApiResponse;
import com.ai.foundation.com.stream.RunStreamEnvelope;
import com.ai.foundation.dal.entity.AgentRun;
import com.ai.foundation.dal.entity.AgentConversationInfo;
import com.ai.foundation.facade.dto.run.CreateRunRequest;
import com.ai.foundation.facade.dto.run.CreateRunResponse;
import com.ai.foundation.facade.dto.run.RunCancelRequest;
import com.ai.foundation.facade.dto.run.RunDetailRequest;
import com.ai.foundation.facade.dto.run.RunDetailResponse;
import com.ai.foundation.facade.dto.run.RunEventsRequest;
import com.ai.foundation.gateway.util.MonoUtils;
import com.ai.foundation.mediator.conversation.AgentConversationMedService;
import com.ai.foundation.mediator.run.AgentRunMedService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/chat/runs")
@RequiredArgsConstructor
public class RunController {

    private final AgentRunMedService runMedService;
    private final AgentConversationMedService conversationMedService;

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

    @PostMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<RunStreamEnvelope> events(@Valid @RequestBody RunEventsRequest request) {
        return runMedService.streamRunEvents(request.getRunCode());
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
