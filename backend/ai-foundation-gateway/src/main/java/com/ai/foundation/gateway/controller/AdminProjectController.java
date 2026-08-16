package com.ai.foundation.gateway.controller;

import com.ai.foundation.com.response.ApiResponse;
import com.ai.foundation.com.response.PageResult;
import com.ai.foundation.facade.dto.cli.BindCapabilitiesRequest;
import com.ai.foundation.facade.dto.cli.BindOptionsResponse;
import com.ai.foundation.facade.dto.project.AgentProjectDTO;
import com.ai.foundation.facade.dto.project.AgentProjectPageRequest;
import com.ai.foundation.facade.dto.project.AgentProjectSaveRequest;
import com.ai.foundation.facade.dto.skill.BindSkillsRequest;
import com.ai.foundation.facade.dto.skill.SkillBindOptionDTO;
import com.ai.foundation.gateway.filter.AdminContext;
import com.ai.foundation.gateway.util.MonoUtils;
import com.ai.foundation.mediator.project.AgentProjectMedService;
import com.ai.foundation.mediator.skill.SkillProjectBinder;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/admin/project")
@RequiredArgsConstructor
public class AdminProjectController {

    private final AgentProjectMedService projectMedService;
    private final SkillProjectBinder skillProjectBinder;

    @GetMapping("/page")
    public Mono<ApiResponse<PageResult<AgentProjectDTO>>> page(AgentProjectPageRequest request) {
        return MonoUtils.fromBlocking(() -> projectMedService.page(request))
                .map(ApiResponse::success);
    }

    @GetMapping("/{id}")
    public Mono<ApiResponse<AgentProjectDTO>> detail(@PathVariable Long id) {
        return MonoUtils.fromBlocking(() -> projectMedService.detail(id))
                .map(ApiResponse::success);
    }

    @PostMapping
    public Mono<ApiResponse<Void>> create(@Valid @RequestBody AgentProjectSaveRequest request,
                                           ServerWebExchange exchange) {
        return MonoUtils.fromBlocking(() -> {
            projectMedService.create(request, AdminContext.currentOperator(exchange));
            return ApiResponse.<Void>success();
        });
    }

    @PutMapping
    public Mono<ApiResponse<Void>> update(@Valid @RequestBody AgentProjectSaveRequest request,
                                          ServerWebExchange exchange) {
        return MonoUtils.fromBlocking(() -> {
            projectMedService.update(request, AdminContext.currentOperator(exchange));
            return ApiResponse.<Void>success();
        });
    }

    @DeleteMapping("/{id}")
    public Mono<ApiResponse<Void>> delete(@PathVariable Long id, ServerWebExchange exchange) {
        return MonoUtils.fromBlocking(() -> {
            projectMedService.delete(id, AdminContext.currentOperator(exchange));
            return ApiResponse.<Void>success();
        });
    }

    @GetMapping("/{id}/bindOptions")
    public Mono<ApiResponse<BindOptionsResponse>> listBindOptions(@PathVariable Long id) {
        return MonoUtils.fromBlocking(() -> projectMedService.listBindOptions(id))
                .map(ApiResponse::success);
    }

    @PostMapping("/bindCapabilities")
    public Mono<ApiResponse<Boolean>> bindCapabilities(@Valid @RequestBody BindCapabilitiesRequest request,
                                                        ServerWebExchange exchange) {
        return MonoUtils.fromBlocking(() -> {
            projectMedService.bindCapabilities(request.getId(), request.getCliIds(),
                    AdminContext.currentOperator(exchange));
            return ApiResponse.success(true);
        });
    }

    @GetMapping("/{id}/skillBindOptions")
    public Mono<ApiResponse<List<SkillBindOptionDTO>>> listSkillBindOptions(@PathVariable Long id) {
        return MonoUtils.fromBlocking(() -> projectMedService.listSkillBindOptions(id))
                .map(ApiResponse::success);
    }

    @PostMapping("/bindSkills")
    public Mono<ApiResponse<Boolean>> bindSkills(@Valid @RequestBody BindSkillsRequest request,
                                                  ServerWebExchange exchange) {
        return MonoUtils.fromBlocking(() -> {
            skillProjectBinder.bindSkills(request.getId(), request.getSkillIds(),
                    AdminContext.currentOperator(exchange));
            return ApiResponse.success(true);
        });
    }
}
