package com.ai.foundation.com.constant;

public final class RedisKeyConstants {

    private RedisKeyConstants() {
    }

    private static final String PREFIX = "ai:foundation:";

    public static final String ADMIN_TOKEN = PREFIX + "admin:token:";
    public static final String PROJECT_CACHE = PREFIX + "project:";
    public static final String MODEL_CONFIG_CACHE = PREFIX + "model:config:";
    public static final String CAPABILITY_CATALOG = PREFIX + "capability:catalog";
    public static final String CHAT_HOT_TURNS = PREFIX + "chat:hot:";
    public static final String RUN_CANCEL_FLAG = PREFIX + "run:cancel:";

    public static String adminToken(String token) {
        return ADMIN_TOKEN + token;
    }

    public static String projectCache(Long projectId) {
        return PROJECT_CACHE + projectId;
    }

    public static String chatHotTurns(Long conversationId) {
        return CHAT_HOT_TURNS + conversationId;
    }

    /**
     * Run 取消标志位 key：{@code ai:foundation:run:cancel:<runCode>}。
     */
    public static String runCancelFlag(String runCode) {
        return RUN_CANCEL_FLAG + runCode;
    }
}
