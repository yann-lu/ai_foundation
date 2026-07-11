package com.ai.foundation.gateway.filter;

import com.ai.foundation.mediator.admin.AdminLoginInfo;
import org.springframework.web.server.ServerWebExchange;

public final class AdminContext {

    public static final String ADMIN_LOGIN_INFO_KEY = "adminLoginInfo";

    private AdminContext() {
    }

    public static AdminLoginInfo get(ServerWebExchange exchange) {
        return (AdminLoginInfo) exchange.getAttributes().get(ADMIN_LOGIN_INFO_KEY);
    }

    public static String currentOperator(ServerWebExchange exchange) {
        AdminLoginInfo info = get(exchange);
        return info != null ? info.getUsername() : "system";
    }
}
