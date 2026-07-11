package com.ai.foundation.gateway.handler;

import com.ai.foundation.com.exception.BusinessException;
import com.ai.foundation.com.response.ApiResponse;
import com.ai.foundation.com.response.ResultCode;
import com.ai.foundation.com.trace.TraceUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import reactor.core.publisher.Mono;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Mono<ResponseEntity<ApiResponse<Void>>> handleBusiness(BusinessException e) {
        log.warn("业务异常 code={} msg={}", e.getResultCode().getCode(), e.getMessage());
        HttpStatus status = mapStatus(e.getResultCode());
        ApiResponse<Void> body = ApiResponse.<Void>fail(e.getResultCode(), e.getMessage()).withTraceId(TraceUtils.newTraceId());
        return Mono.just(ResponseEntity.status(status).body(body));
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public Mono<ResponseEntity<ApiResponse<Void>>> handleValidation(WebExchangeBindException e) {
        String message = e.getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", message);
        ApiResponse<Void> body = ApiResponse.<Void>fail(ResultCode.PARAM_INVALID, message)
                .withTraceId(TraceUtils.newTraceId());
        return Mono.just(ResponseEntity.badRequest().body(body));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Mono<ResponseEntity<ApiResponse<Void>>> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("非法参数: {}", e.getMessage());
        ApiResponse<Void> body = ApiResponse.<Void>fail(ResultCode.PARAM_INVALID, e.getMessage())
                .withTraceId(TraceUtils.newTraceId());
        return Mono.just(ResponseEntity.badRequest().body(body));
    }

    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<ApiResponse<Void>>> handleOther(Exception e) {
        log.error("系统异常", e);
        ApiResponse<Void> body = ApiResponse.<Void>fail(ResultCode.SYSTEM_ERROR)
                .withTraceId(TraceUtils.newTraceId());
        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body));
    }

    private HttpStatus mapStatus(ResultCode resultCode) {
        return switch (resultCode) {
            case UNAUTHORIZED, LOGIN_FAILED -> HttpStatus.UNAUTHORIZED;
            case PARAM_INVALID -> HttpStatus.BAD_REQUEST;
            case DATA_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case DATA_DUPLICATED, STATE_INVALID -> HttpStatus.CONFLICT;
            default -> HttpStatus.OK;
        };
    }
}
