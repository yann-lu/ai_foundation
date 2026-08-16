package com.ai.foundation.gateway.controller;

import com.ai.foundation.com.response.ApiResponse;
import com.ai.foundation.com.response.PageResult;
import com.ai.foundation.facade.dto.cli.CliCommandDTO;
import com.ai.foundation.facade.dto.cli.CliCommandDetailDTO;
import com.ai.foundation.facade.dto.cli.CliCommandPageRequest;
import com.ai.foundation.facade.dto.cli.CliCommandSaveRequest;
import com.ai.foundation.gateway.filter.AdminContext;
import com.ai.foundation.gateway.util.MonoUtils;
import com.ai.foundation.mediator.cli.AgentCliMedService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/admin/cli")
@RequiredArgsConstructor
public class AdminCliController {

    private final AgentCliMedService cliMedService;

    @PostMapping("/page")
    public Mono<ApiResponse<PageResult<CliCommandDTO>>> page(
            @RequestBody CliCommandPageRequest request) {
        return MonoUtils.fromBlocking(() -> cliMedService.page(request))
                .map(ApiResponse::success);
    }

    @GetMapping("/{id}")
    public Mono<ApiResponse<CliCommandDetailDTO>> detail(@PathVariable Long id) {
        return MonoUtils.fromBlocking(() -> cliMedService.detail(id))
                .map(ApiResponse::success);
    }

    @PostMapping
    public Mono<ApiResponse<Long>> create(@Valid @RequestBody CliCommandSaveRequest request,
                                           ServerWebExchange exchange) {
        return MonoUtils.fromBlocking(() -> {
            Long id = cliMedService.create(request, AdminContext.currentOperator(exchange));
            return ApiResponse.success(id);
        });
    }

    @PutMapping
    public Mono<ApiResponse<Boolean>> update(@Valid @RequestBody CliCommandSaveRequest request,
                                              ServerWebExchange exchange) {
        return MonoUtils.fromBlocking(() -> {
            cliMedService.update(request, AdminContext.currentOperator(exchange));
            return ApiResponse.success(true);
        });
    }

    @DeleteMapping("/{id}")
    public Mono<ApiResponse<Boolean>> delete(@PathVariable Long id, ServerWebExchange exchange) {
        return MonoUtils.fromBlocking(() -> {
            cliMedService.delete(id, AdminContext.currentOperator(exchange));
            return ApiResponse.success(true);
        });
    }
}
