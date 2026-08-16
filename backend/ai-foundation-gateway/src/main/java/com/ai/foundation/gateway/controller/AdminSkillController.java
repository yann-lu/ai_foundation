package com.ai.foundation.gateway.controller;

import com.ai.foundation.com.response.ApiResponse;
import com.ai.foundation.com.response.PageResult;
import com.ai.foundation.facade.dto.skill.AgentSkillDTO;
import com.ai.foundation.facade.dto.skill.AgentSkillPageRequest;
import com.ai.foundation.facade.dto.skill.AgentSkillSaveRequest;
import com.ai.foundation.facade.dto.skill.SkillBindOptionDTO;
import com.ai.foundation.gateway.filter.AdminContext;
import com.ai.foundation.gateway.util.MonoUtils;
import com.ai.foundation.mediator.skill.AgentSkillMedService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/admin/skill")
@RequiredArgsConstructor
public class AdminSkillController {

    private final AgentSkillMedService skillMedService;

    @GetMapping("/page")
    public Mono<ApiResponse<PageResult<AgentSkillDTO>>> page(AgentSkillPageRequest request) {
        return MonoUtils.fromBlocking(() -> skillMedService.page(request))
                .map(ApiResponse::success);
    }

    @GetMapping("/{id}")
    public Mono<ApiResponse<AgentSkillDTO>> detail(@PathVariable Long id) {
        return MonoUtils.fromBlocking(() -> skillMedService.detail(id))
                .map(ApiResponse::success);
    }

    @PostMapping
    public Mono<ApiResponse<Void>> create(@Valid @RequestBody AgentSkillSaveRequest request,
                                           ServerWebExchange exchange) {
        return MonoUtils.fromBlocking(() -> {
            skillMedService.save(request, AdminContext.currentOperator(exchange));
            return ApiResponse.<Void>success();
        });
    }

    @PutMapping
    public Mono<ApiResponse<Void>> update(@Valid @RequestBody AgentSkillSaveRequest request,
                                          ServerWebExchange exchange) {
        return MonoUtils.fromBlocking(() -> {
            skillMedService.save(request, AdminContext.currentOperator(exchange));
            return ApiResponse.<Void>success();
        });
    }

    @DeleteMapping("/{id}")
    public Mono<ApiResponse<Void>> delete(@PathVariable Long id, ServerWebExchange exchange) {
        return MonoUtils.fromBlocking(() -> {
            skillMedService.delete(id, AdminContext.currentOperator(exchange));
            return ApiResponse.<Void>success();
        });
    }

    @GetMapping("/bindOptions/{projectId}")
    public Mono<ApiResponse<List<SkillBindOptionDTO>>> listBindOptions(@PathVariable Long projectId) {
        return MonoUtils.fromBlocking(() -> skillMedService.listBindOptions(projectId))
                .map(ApiResponse::success);
    }
}
