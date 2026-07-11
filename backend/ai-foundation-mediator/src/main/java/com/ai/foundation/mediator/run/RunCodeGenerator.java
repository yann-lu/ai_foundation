package com.ai.foundation.mediator.run;

import java.util.UUID;

public final class RunCodeGenerator {

    private RunCodeGenerator() {
    }

    public static String generate() {
        return "run_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
