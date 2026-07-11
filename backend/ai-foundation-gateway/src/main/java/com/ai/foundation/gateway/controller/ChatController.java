package com.ai.foundation.gateway.controller;

import com.ai.foundation.com.response.ApiResponse;
import com.ai.foundation.facade.dto.chat.ChatStreamChunkDTO;
import com.ai.foundation.facade.dto.chat.ChatSyncRequest;
import com.ai.foundation.facade.dto.chat.ChatSyncResponse;
import com.ai.foundation.facade.dto.chat.ChatStreamRequest;
import com.ai.foundation.facade.dto.conversation.ConversationCreateRequest;
import com.ai.foundation.facade.dto.conversation.ConversationDTO;
import com.ai.foundation.facade.dto.conversation.MessageDTO;
import com.ai.foundation.gateway.util.MonoUtils;
import com.ai.foundation.mediator.chat.AiChatMedService;
import com.ai.foundation.mediator.conversation.AgentConversationMedService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {

    private final AgentConversationMedService conversationMedService;
    private final AiChatMedService chatMedService;

    @PostMapping("/create")
    public Mono<ApiResponse<ConversationDTO>> create(@Valid @RequestBody ConversationCreateRequest request) {
        return MonoUtils.fromBlocking(() -> conversationMedService.create(request))
                .map(ApiResponse::success);
    }

    @GetMapping("/messages/{conversationCode}")
    public Mono<ApiResponse<List<MessageDTO>>> messages(
            @PathVariable String conversationCode,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(defaultValue = "50") int limit) {
        return MonoUtils.fromBlocking(() -> conversationMedService.messages(conversationCode, beforeId, limit))
                .map(ApiResponse::success);
    }

    @PostMapping("/sync")
    public Mono<ApiResponse<ChatSyncResponse>> sync(
            @Valid @RequestBody ChatSyncRequest request,
            ServerWebExchange exchange) {
        String clientIp = extractClientIp(exchange);
        return MonoUtils.fromBlocking(() -> chatMedService.syncChat(request, clientIp))
                .map(ApiResponse::success);
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ChatStreamChunkDTO> stream(
            @Valid @RequestBody ChatStreamRequest request,
            ServerWebExchange exchange) {
        String clientIp = extractClientIp(exchange);
        return chatMedService.streamChat(request, clientIp);
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
