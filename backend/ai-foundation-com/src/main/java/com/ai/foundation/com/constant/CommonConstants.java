package com.ai.foundation.com.constant;

public final class CommonConstants {

    private CommonConstants() {
    }

    public static final String HEADER_ADMIN_TOKEN = "x-admin-token";
    public static final String HEADER_TRACE_ID = "x-trace-id";
    public static final String TRACE_ID_KEY = "traceId";

    public static final String DEFAULT_CREATE_USER = "system";
    public static final String DEFAULT_MODIFY_USER = "system";

    public static final int STATE_ENABLED = 1;
    public static final int STATE_DISABLED = 0;
    public static final int NOT_DELETED = 0;
    public static final int DELETED = 1;
}
