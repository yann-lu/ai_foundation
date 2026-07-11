package com.ai.foundation.com.constant;

public final class RedisKeyConstants {

    private RedisKeyConstants() {
    }

    private static final String PREFIX = "ai:foundation:";

    public static final String ADMIN_TOKEN = PREFIX + "admin:token:";
    public static final String PROJECT_CACHE = PREFIX + "project:";
    public static final String MODEL_CONFIG_CACHE = PREFIX + "model:config:";
    public static final String CAPABILITY_CATALOG = PREFIX + "capability:catalog";

    public static String adminToken(String token) {
        return ADMIN_TOKEN + token;
    }

    public static String projectCache(Long projectId) {
        return PROJECT_CACHE + projectId;
    }
}
