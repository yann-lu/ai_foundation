package com.ai.foundation.com.response;

import lombok.Data;

@Data
public class ApiResponse<T> {

    private boolean success;
    private String code;
    private String message;
    private T data;
    private String traceId;

    private ApiResponse() {
    }

    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> resp = new ApiResponse<>();
        resp.success = true;
        resp.code = ResultCode.SUCCESS.getCode();
        resp.message = ResultCode.SUCCESS.getMessage();
        resp.data = data;
        return resp;
    }

    public static <T> ApiResponse<T> success() {
        return success(null);
    }

    public static <T> ApiResponse<T> fail(ResultCode resultCode) {
        ApiResponse<T> resp = new ApiResponse<>();
        resp.success = false;
        resp.code = resultCode.getCode();
        resp.message = resultCode.getMessage();
        return resp;
    }

    public static <T> ApiResponse<T> fail(ResultCode resultCode, String message) {
        ApiResponse<T> resp = new ApiResponse<>();
        resp.success = false;
        resp.code = resultCode.getCode();
        resp.message = message;
        return resp;
    }

    public ApiResponse<T> withTraceId(String traceId) {
        this.traceId = traceId;
        return this;
    }
}
