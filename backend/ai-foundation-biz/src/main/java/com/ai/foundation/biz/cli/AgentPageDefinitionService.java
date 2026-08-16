package com.ai.foundation.biz.cli;

import com.ai.foundation.dal.entity.AgentPageDefinition;
import com.baomidou.mybatisplus.extension.service.IService;

public interface AgentPageDefinitionService extends IService<AgentPageDefinition> {

    AgentPageDefinition getByCliId(Long cliId);

    void removeByCliId(Long cliId);
}
