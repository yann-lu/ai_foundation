package com.ai.foundation.mediator.admin;

import com.ai.foundation.com.constant.RedisKeyConstants;
import com.ai.foundation.com.exception.BusinessException;
import com.ai.foundation.com.response.ResultCode;
import com.ai.foundation.com.trace.TraceUtils;
import com.ai.foundation.facade.dto.admin.AdminLoginRequest;
import com.ai.foundation.facade.dto.admin.AdminLoginResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAuthMedService {

    private static final Duration TOKEN_TTL = Duration.ofHours(8);

    private final AdminAccountProperties accountProperties;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public AdminLoginResponse login(AdminLoginRequest request) {
        AdminAccountProperties.AdminAccount account = accountProperties.getAccounts().stream()
                .filter(a -> a.getUsername().equals(request.getUsername())
                        && a.getPassword().equals(request.getPassword()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ResultCode.LOGIN_FAILED));

        String token = TraceUtils.newTraceId();
        AdminLoginInfo loginInfo = new AdminLoginInfo(account.getUsername(), account.getNickname());
        try {
            String json = objectMapper.writeValueAsString(loginInfo);
            redisTemplate.opsForValue().set(RedisKeyConstants.adminToken(token), json, TOKEN_TTL);
        } catch (Exception e) {
            log.error("保存管理员登录态失败", e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR);
        }
        log.info("管理员登录成功 username={}", account.getUsername());
        return new AdminLoginResponse(token, account.getUsername(), account.getNickname());
    }

    public AdminLoginInfo verifyToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String json = redisTemplate.opsForValue().get(RedisKeyConstants.adminToken(token));
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, AdminLoginInfo.class);
        } catch (Exception e) {
            log.warn("解析管理员登录态失败 token={}", token);
            return null;
        }
    }
}
