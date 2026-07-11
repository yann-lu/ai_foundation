package com.ai.foundation.biz.model;

import com.ai.foundation.dal.entity.AgentModelConfig;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

public interface AgentModelConfigService extends IService<AgentModelConfig> {

    IPage<AgentModelConfig> page(Page<AgentModelConfig> page, Long projectId, String modelName, String modelType, Integer state);
}
