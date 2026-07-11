package com.ai.foundation.mediator.model;

import com.ai.foundation.biz.model.AgentModelConfigService;
import com.ai.foundation.com.constant.CommonConstants;
import com.ai.foundation.com.enums.ModelTypeEnum;
import com.ai.foundation.dal.entity.AgentModelConfig;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentModelResolver {

    private final AgentModelConfigService modelConfigService;
    private final AiModelsProperties aiModelsProperties;

    public String resolveChatModel(Long projectId) {
        return resolveModel(projectId, ModelTypeEnum.CHAT, aiModelsProperties.getChat());
    }

    public String resolveEmbeddingModel(Long projectId) {
        return resolveModel(projectId, ModelTypeEnum.EMBEDDING, aiModelsProperties.getEmbedding());
    }

    private String resolveModel(Long projectId, ModelTypeEnum type, String fallback) {
        if (projectId != null) {
            AgentModelConfig config = modelConfigService.getOne(new LambdaQueryWrapper<AgentModelConfig>()
                    .eq(AgentModelConfig::getProjectId, projectId)
                    .eq(AgentModelConfig::getModelType, type.getCode())
                    .eq(AgentModelConfig::getState, CommonConstants.STATE_ENABLED)
                    .last("limit 1"));
            if (config != null) {
                return config.getModelName();
            }
        }
        log.debug("项目未配置{}模型，使用系统默认: {}", type.getCode(), fallback);
        return fallback;
    }
}
