package com.ai.foundation.biz.cli;

import com.ai.foundation.dal.entity.AgentCliParam;
import com.ai.foundation.dal.mapper.AgentCliParamMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AgentCliParamServiceImpl extends ServiceImpl<AgentCliParamMapper, AgentCliParam>
        implements AgentCliParamService {

    @Override
    public List<AgentCliParam> listByCliId(Long cliId) {
        return this.list(new LambdaQueryWrapper<AgentCliParam>()
                .eq(AgentCliParam::getCliId, cliId)
                .orderByAsc(AgentCliParam::getSortOrder));
    }

    @Override
    public void removeByCliId(Long cliId) {
        this.remove(new LambdaQueryWrapper<AgentCliParam>()
                .eq(AgentCliParam::getCliId, cliId));
    }
}
