package com.ai.foundation.gateway.controller;

import com.ai.foundation.com.response.ApiResponse;
import com.ai.foundation.facade.dto.admin.AdminLoginRequest;
import com.ai.foundation.facade.dto.admin.AdminLoginResponse;
import com.ai.foundation.gateway.util.MonoUtils;
import com.ai.foundation.mediator.admin.AdminAuthMedService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminAuthMedService adminAuthMedService;

    @PostMapping("/login")
    public Mono<ApiResponse<AdminLoginResponse>> login(@Valid @RequestBody AdminLoginRequest request) {
        return MonoUtils.fromBlocking(() -> adminAuthMedService.login(request))
                .map(ApiResponse::success);
    }
}
