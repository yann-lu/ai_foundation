package com.ai.foundation.com.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ResultCode {

    SUCCESS("00000", "成功"),
    PARAM_INVALID("A0001", "参数校验失败"),
    UNAUTHORIZED("A0101", "未授权或登录已失效"),
    LOGIN_FAILED("A0102", "账号或密码错误"),
    DATA_NOT_FOUND("A0201", "数据不存在"),
    DATA_DUPLICATED("A0202", "数据已存在"),
    STATE_INVALID("A0203", "当前状态不允许该操作"),
    SYSTEM_ERROR("B0001", "系统繁忙，请稍后再试"),
    SERVICE_UNAVAILABLE("B0002", "服务暂不可用");

    private final String code;
    private final String message;
}
