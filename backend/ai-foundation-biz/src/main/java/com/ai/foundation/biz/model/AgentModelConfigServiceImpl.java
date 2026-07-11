package com.ai.foundation.biz.model;

import com.ai.foundation.dal.entity.AgentModelConfig;
import com.ai.foundation.dal.mapper.AgentModelConfigMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class AgentModelConfigServiceImpl extends ServiceImpl<AgentModelConfigMapper, AgentModelConfig>
        implements AgentModelConfigService {

    @Override
    public IPage<AgentModelConfig> page(Page<AgentModelConfig> page, Long projectId, String modelName, String modelType, Integer state) {
        LambdaQueryWrapper<AgentModelConfig> wrapper = new LambdaQueryWrapper<AgentModelConfig>()
                .eq(projectId != null, AgentModelConfig::getProjectId, projectId)
                .like(modelName != null && !modelName.isBlank(), AgentModelConfig::getModelName, modelName)
                .eq(modelType != null && !modelType.isBlank(), AgentModelConfig::getModelType, modelType)
                .eq(state != null, AgentModelConfig::getState, state)
                .orderByDesc(AgentModelConfig::getUpdateTime);
        return this.page(page, wrapper);
    }
}
