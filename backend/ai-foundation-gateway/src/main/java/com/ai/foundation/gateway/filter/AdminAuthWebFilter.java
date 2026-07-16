package com.ai.foundation.gateway.filter;

import com.ai.foundation.com.constant.CommonConstants;
import com.ai.foundation.com.response.ApiResponse;
import com.ai.foundation.com.response.ResultCode;
import com.ai.foundation.gateway.util.MonoUtils;
import com.ai.foundation.mediator.admin.AdminAuthMedService;
import com.ai.foundation.mediator.admin.AdminLoginInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AdminAuthWebFilter implements WebFilter {

    private static final String LOGIN_PATH = "/admin/auth/login";

    private final AdminAuthMedService adminAuthMedService;
    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (!path.startsWith("/admin/") || LOGIN_PATH.equals(path)) {
            return chain.filter(exchange);
        }
        String token = exchange.getRequest().getHeaders().getFirst(CommonConstants.HEADER_ADMIN_TOKEN);
        if (token == null || token.isBlank()) {
            return writeUnauthorized(exchange);
        }
        return MonoUtils.fromBlocking(() -> Optional.ofNullable(adminAuthMedService.verifyToken(token)))
                .flatMap(optional -> {
                    if (optional.isEmpty()) {
                        return writeUnauthorized(exchange);
                    }
                    exchange.getAttributes().put(AdminContext.ADMIN_LOGIN_INFO_KEY, optional.get());
                    return chain.filter(exchange);
                });
    }

    private Mono<Void> writeUnauthorized(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            String body = objectMapper.writeValueAsString(ApiResponse.fail(ResultCode.UNAUTHORIZED));
            return response.writeWith(
                    Mono.just(response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8))));
        } catch (Exception e) {
            log.error("序列化未授权响应失败", e);
            return response.setComplete();
        }
    }
}
