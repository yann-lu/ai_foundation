package com.ai.foundation.mediator.chat;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Run Inspector 展示用的模型请求消息。
 */
@Getter
@AllArgsConstructor
public class ChatRequestMessage {

    private final String role;

    private final String content;
}
