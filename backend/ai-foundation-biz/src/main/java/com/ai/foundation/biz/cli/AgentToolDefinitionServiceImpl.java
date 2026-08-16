package com.ai.foundation.biz.cli;

import com.ai.foundation.dal.entity.AgentToolDefinition;
import com.ai.foundation.dal.mapper.AgentToolDefinitionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class AgentToolDefinitionServiceImpl extends ServiceImpl<AgentToolDefinitionMapper, AgentToolDefinition>
        implements AgentToolDefinitionService {

    @Override
    public AgentToolDefinition getByCliId(Long cliId) {
        return this.getOne(new LambdaQueryWrapper<AgentToolDefinition>()
                .eq(AgentToolDefinition::getCliId, cliId)
                .last("limit 1"));
    }

    @Override
    public void removeByCliId(Long cliId) {
        this.remove(new LambdaQueryWrapper<AgentToolDefinition>()
                .eq(AgentToolDefinition::getCliId, cliId));
    }
}
