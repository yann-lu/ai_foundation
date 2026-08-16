package com.ai.foundation.biz.schema;

import com.ai.foundation.dal.entity.AgentApiSchemaConfig;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface AgentApiSchemaConfigService extends IService<AgentApiSchemaConfig> {

    IPage<AgentApiSchemaConfig> page(Page<AgentApiSchemaConfig> page, String keyword, Integer state);

    boolean existsByCode(String schemaCode, Long excludeId);

    List<AgentApiSchemaConfig> listEnabled();

    AgentApiSchemaConfig getBySchemaCode(String schemaCode);
}
