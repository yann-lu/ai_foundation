package com.ai.foundation.mediator.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 平台 agent.* 配置项。
 * <p>
 * 当前仅含 ReAct 运行相关配置（activation-body-preview-max）；
 * 后续可扩展 retrieval / model resolver 等配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "agent")
public class AgentProperties {

    private React react = new React();

    @Data
    public static class React {
        /**
         * skill_* 工具激活时回传给模型的正文预览上限（字符）。
         * 完整 body 缓存在 ReactRunSession.activatedSkill；超过该上限会截断。
         * <= 0 时回退默认 4000。
         */
        private int activationBodyPreviewMax = 4000;
    }
}
