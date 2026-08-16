package com.ai.foundation.gateway.controller;

import com.ai.foundation.com.response.ApiResponse;
import com.ai.foundation.com.response.PageResult;
import com.ai.foundation.facade.dto.schema.ApiSchemaConfigDTO;
import com.ai.foundation.facade.dto.schema.ApiSchemaConfigPageRequest;
import com.ai.foundation.facade.dto.schema.ApiSchemaConfigSaveRequest;
import com.ai.foundation.gateway.filter.AdminContext;
import com.ai.foundation.gateway.util.MonoUtils;
import com.ai.foundation.mediator.schema.AgentApiSchemaMedService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/admin/apiSchema")
@RequiredArgsConstructor
public class AdminApiSchemaController {

    private final AgentApiSchemaMedService schemaMedService;

    @PostMapping("/page")
    public Mono<ApiResponse<PageResult<ApiSchemaConfigDTO>>> page(
            @RequestBody ApiSchemaConfigPageRequest request) {
        return MonoUtils.fromBlocking(() -> schemaMedService.page(request))
                .map(ApiResponse::success);
    }

    @GetMapping("/{id}")
    public Mono<ApiResponse<ApiSchemaConfigDTO>> detail(@PathVariable Long id) {
        return MonoUtils.fromBlocking(() -> schemaMedService.detail(id))
                .map(ApiResponse::success);
    }

    @GetMapping("/listEnabled")
    public Mono<ApiResponse<List<ApiSchemaConfigDTO>>> listEnabled() {
        return MonoUtils.fromBlocking(() -> schemaMedService.listEnabled())
                .map(ApiResponse::success);
    }

    @PostMapping
    public Mono<ApiResponse<Long>> create(@Valid @RequestBody ApiSchemaConfigSaveRequest request,
                                           ServerWebExchange exchange) {
        return MonoUtils.fromBlocking(() -> {
            Long id = schemaMedService.create(request, AdminContext.currentOperator(exchange));
            return ApiResponse.success(id);
        });
    }

    @PutMapping
    public Mono<ApiResponse<Boolean>> update(@Valid @RequestBody ApiSchemaConfigSaveRequest request,
                                              ServerWebExchange exchange) {
        return MonoUtils.fromBlocking(() -> {
            schemaMedService.update(request, AdminContext.currentOperator(exchange));
            return ApiResponse.success(true);
        });
    }

    @DeleteMapping("/{id}")
    public Mono<ApiResponse<Boolean>> delete(@PathVariable Long id, ServerWebExchange exchange) {
        return MonoUtils.fromBlocking(() -> {
            schemaMedService.delete(id, AdminContext.currentOperator(exchange));
            return ApiResponse.success(true);
        });
    }
}
