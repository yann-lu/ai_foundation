package com.ai.foundation.gateway.controller;

import com.ai.foundation.com.response.ApiResponse;
import com.ai.foundation.com.response.PageResult;
import com.ai.foundation.facade.dto.model.AgentModelConfigDTO;
import com.ai.foundation.facade.dto.model.AgentModelConfigPageRequest;
import com.ai.foundation.facade.dto.model.AgentModelConfigSaveRequest;
import com.ai.foundation.gateway.filter.AdminContext;
import com.ai.foundation.gateway.util.MonoUtils;
import com.ai.foundation.mediator.model.AgentModelConfigMedService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/admin/model")
@RequiredArgsConstructor
public class AdminModelConfigController {

    private final AgentModelConfigMedService modelConfigMedService;

    @GetMapping("/page")
    public Mono<ApiResponse<PageResult<AgentModelConfigDTO>>> page(AgentModelConfigPageRequest request) {
        return MonoUtils.fromBlocking(() -> modelConfigMedService.page(request))
                .map(ApiResponse::success);
    }

    @GetMapping("/{id}")
    public Mono<ApiResponse<AgentModelConfigDTO>> detail(@PathVariable Long id) {
        return MonoUtils.fromBlocking(() -> modelConfigMedService.detail(id))
                .map(ApiResponse::success);
    }

    @PostMapping
    public Mono<ApiResponse<Void>> create(@Valid @RequestBody AgentModelConfigSaveRequest request,
                                          ServerWebExchange exchange) {
        return MonoUtils.fromBlocking(() -> {
            modelConfigMedService.create(request, AdminContext.currentOperator(exchange));
            return ApiResponse.<Void>success();
        });
    }

    @PutMapping
    public Mono<ApiResponse<Void>> update(@Valid @RequestBody AgentModelConfigSaveRequest request,
                                          ServerWebExchange exchange) {
        return MonoUtils.fromBlocking(() -> {
            modelConfigMedService.update(request, AdminContext.currentOperator(exchange));
            return ApiResponse.<Void>success();
        });
    }

    @DeleteMapping("/{id}")
    public Mono<ApiResponse<Void>> delete(@PathVariable Long id, ServerWebExchange exchange) {
        return MonoUtils.fromBlocking(() -> {
            modelConfigMedService.delete(id, AdminContext.currentOperator(exchange));
            return ApiResponse.<Void>success();
        });
    }
}
