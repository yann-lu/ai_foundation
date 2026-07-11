package com.ai.foundation.com.trace;

import java.util.UUID;

public final class TraceUtils {

    private TraceUtils() {
    }

    public static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
