package com.ai.foundation.biz.cli;

import com.ai.foundation.dal.entity.AgentToolDefinition;
import com.baomidou.mybatisplus.extension.service.IService;

public interface AgentToolDefinitionService extends IService<AgentToolDefinition> {

    AgentToolDefinition getByCliId(Long cliId);

    void removeByCliId(Long cliId);

    long countByMcpServerId(Long mcpServerId);
}
