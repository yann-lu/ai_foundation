package com.ai.foundation.gateway.controller;

import com.ai.foundation.com.response.ApiResponse;
import com.ai.foundation.com.response.PageResult;
import com.ai.foundation.facade.dto.mcp.AgentMcpServerDTO;
import com.ai.foundation.facade.dto.mcp.AgentMcpServerPageRequest;
import com.ai.foundation.facade.dto.mcp.AgentMcpServerSaveRequest;
import com.ai.foundation.gateway.filter.AdminContext;
import com.ai.foundation.gateway.util.MonoUtils;
import com.ai.foundation.mediator.mcp.AgentMcpServerMedService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/admin/mcp")
@RequiredArgsConstructor
public class AdminMcpController {

    private final AgentMcpServerMedService mcpServerMedService;

    @GetMapping("/page")
    public Mono<ApiResponse<PageResult<AgentMcpServerDTO>>> page(AgentMcpServerPageRequest request) {
        return MonoUtils.fromBlocking(() -> mcpServerMedService.page(request))
                .map(ApiResponse::success);
    }

    @GetMapping("/{id}")
    public Mono<ApiResponse<AgentMcpServerDTO>> detail(@PathVariable Long id) {
        return MonoUtils.fromBlocking(() -> mcpServerMedService.detail(id))
                .map(ApiResponse::success);
    }

    @PostMapping
    public Mono<ApiResponse<Void>> create(@Valid @RequestBody AgentMcpServerSaveRequest request,
                                          ServerWebExchange exchange) {
        return MonoUtils.fromBlocking(() -> {
            mcpServerMedService.save(request, AdminContext.currentOperator(exchange));
            return ApiResponse.<Void>success();
        });
    }

    @PutMapping
    public Mono<ApiResponse<Void>> update(@Valid @RequestBody AgentMcpServerSaveRequest request,
                                          ServerWebExchange exchange) {
        return MonoUtils.fromBlocking(() -> {
            mcpServerMedService.save(request, AdminContext.currentOperator(exchange));
            return ApiResponse.<Void>success();
        });
    }

    @DeleteMapping("/{id}")
    public Mono<ApiResponse<Void>> delete(@PathVariable Long id, ServerWebExchange exchange) {
        return MonoUtils.fromBlocking(() -> {
            mcpServerMedService.delete(id, AdminContext.currentOperator(exchange));
            return ApiResponse.<Void>success();
        });
    }
}
