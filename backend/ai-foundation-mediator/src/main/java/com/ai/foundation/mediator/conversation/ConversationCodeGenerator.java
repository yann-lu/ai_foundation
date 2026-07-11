package com.ai.foundation.mediator.conversation;

import java.security.SecureRandom;

public final class ConversationCodeGenerator {

    private ConversationCodeGenerator() {
    }

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] CHARS = "abcdefghijklmnopqrstuvwxyz0123456789".toCharArray();

    public static String generate() {
        StringBuilder sb = new StringBuilder("conv_");
        sb.append(System.currentTimeMillis());
        sb.append('_');
        for (int i = 0; i < 6; i++) {
            sb.append(CHARS[RANDOM.nextInt(CHARS.length)]);
        }
        return sb.toString();
    }
}
