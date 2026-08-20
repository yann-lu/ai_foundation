package com.ai.foundation.gateway.controller;

import com.ai.foundation.com.response.ApiResponse;
import com.ai.foundation.facade.dto.conversation.ConversationCreateRequest;
import com.ai.foundation.facade.dto.conversation.ConversationDTO;
import com.ai.foundation.facade.dto.conversation.MessageDTO;
import com.ai.foundation.gateway.util.MonoUtils;
import com.ai.foundation.mediator.conversation.AgentConversationMedService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 会话管理 Controller（用户面）。
 * <p>仅承担 ReAct Run 的会话创建与历史消息查询；纯 Chat 模式已下线。
 */
@Slf4j
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ConversationController {

    private final AgentConversationMedService conversationMedService;

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
}
