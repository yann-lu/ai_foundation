package com.ai.foundation.biz.cli;

import com.ai.foundation.dal.entity.AgentPageDefinition;
import com.ai.foundation.dal.mapper.AgentPageDefinitionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class AgentPageDefinitionServiceImpl extends ServiceImpl<AgentPageDefinitionMapper, AgentPageDefinition>
        implements AgentPageDefinitionService {

    @Override
    public AgentPageDefinition getByCliId(Long cliId) {
        return this.getOne(new LambdaQueryWrapper<AgentPageDefinition>()
                .eq(AgentPageDefinition::getCliId, cliId)
                .last("limit 1"));
    }

    @Override
    public void removeByCliId(Long cliId) {
        this.remove(new LambdaQueryWrapper<AgentPageDefinition>()
                .eq(AgentPageDefinition::getCliId, cliId));
    }
}
