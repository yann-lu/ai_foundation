package com.ai.foundation.gateway.controller;

import com.ai.foundation.com.response.ApiResponse;
import com.ai.foundation.com.response.PageResult;
import com.ai.foundation.facade.dto.conversation.ConversationDTO;
import com.ai.foundation.facade.dto.conversation.ConversationDetailDTO;
import com.ai.foundation.facade.dto.conversation.ConversationPageRequest;
import com.ai.foundation.gateway.util.MonoUtils;
import com.ai.foundation.mediator.conversation.AgentConversationMedService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/admin/conversation")
@RequiredArgsConstructor
public class AdminConversationController {

    private final AgentConversationMedService conversationMedService;

    @GetMapping("/page")
    public Mono<ApiResponse<PageResult<ConversationDTO>>> page(ConversationPageRequest request) {
        return MonoUtils.fromBlocking(() -> conversationMedService.page(request))
                .map(ApiResponse::success);
    }

    @GetMapping("/{id}")
    public Mono<ApiResponse<ConversationDetailDTO>> detail(@PathVariable Long id) {
        return MonoUtils.fromBlocking(() -> conversationMedService.detail(id))
                .map(ApiResponse::success);
    }

    @DeleteMapping("/{id}")
    public Mono<ApiResponse<Void>> delete(@PathVariable Long id) {
        return MonoUtils.fromBlocking(() -> {
            conversationMedService.delete(id);
            return ApiResponse.<Void>success();
        });
    }

    @PostMapping("/{id}/clear-messages")
    public Mono<ApiResponse<Void>> clearMessages(@PathVariable Long id) {
        return MonoUtils.fromBlocking(() -> {
            conversationMedService.clearMessages(id);
            return ApiResponse.<Void>success();
        });
    }
}
