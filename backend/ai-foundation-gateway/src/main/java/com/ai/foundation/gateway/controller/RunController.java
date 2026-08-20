package com.ai.foundation.gateway.controller;

import com.ai.foundation.com.response.ApiResponse;
import com.ai.foundation.com.response.PageResult;
import com.ai.foundation.com.stream.RunStreamEnvelope;
import com.ai.foundation.facade.dto.run.CreateRunRequest;
import com.ai.foundation.facade.dto.run.CreateRunResponse;
import com.ai.foundation.facade.dto.run.RunCancelRequest;
import com.ai.foundation.facade.dto.run.RunDetailRequest;
import com.ai.foundation.facade.dto.run.RunDetailResponse;
import com.ai.foundation.facade.dto.run.RunEventDTO;
import com.ai.foundation.facade.dto.run.RunItemDTO;
import com.ai.foundation.gateway.util.MonoUtils;
import com.ai.foundation.mediator.run.AgentRunMedService;
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

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/chat/runs")
@RequiredArgsConstructor
public class RunController {

    private final AgentRunMedService runMedService;

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
        return MonoUtils.fromBlocking(() -> runMedService.getRunDetail(request.getRunCode()))
                .map(ApiResponse::success);
    }

    @GetMapping("/latestDetail")
    public Mono<ApiResponse<RunDetailResponse>> latestDetail(
            @RequestParam String conversationCode) {
        return MonoUtils.fromBlocking(() -> runMedService.getLatestRunDetail(conversationCode))
                .map(ApiResponse::success);
    }

    @GetMapping("/events/list")
    public Mono<ApiResponse<List<RunEventDTO>>> listEvents(@RequestParam String runCode) {
        return MonoUtils.fromBlocking(() -> runMedService.listRunEvents(runCode))
                .map(ApiResponse::success);
    }

    /**
     * 拉取指定会话下所有 Run 的事件日志，用于轨迹页拼接多轮对话。
     * GET /chat/runs/events/by-conversation?conversationCode=xxx
     */
    @GetMapping("/events/by-conversation")
    public Mono<ApiResponse<List<RunEventDTO>>> listEventsByConversation(
            @RequestParam String conversationCode) {
        return MonoUtils.fromBlocking(() -> runMedService.listRunEventsByConversation(conversationCode))
                .map(ApiResponse::success);
    }

    @GetMapping("/page")
    public Mono<ApiResponse<PageResult<RunItemDTO>>> pageRuns(
            @RequestParam String conversationCode,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return MonoUtils.fromBlocking(() -> runMedService.pageRunItems(conversationCode, current, size))
                .map(ApiResponse::success);
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
