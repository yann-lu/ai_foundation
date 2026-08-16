package com.ai.foundation.biz.cli;

import com.ai.foundation.dal.entity.AgentCliRecallTag;
import com.ai.foundation.dal.mapper.AgentCliRecallTagMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AgentCliRecallTagServiceImpl extends ServiceImpl<AgentCliRecallTagMapper, AgentCliRecallTag>
        implements AgentCliRecallTagService {

    @Override
    public List<AgentCliRecallTag> listByCliId(Long cliId) {
        return this.list(new LambdaQueryWrapper<AgentCliRecallTag>()
                .eq(AgentCliRecallTag::getCliId, cliId)
                .eq(AgentCliRecallTag::getState, 1)
                .orderByAsc(AgentCliRecallTag::getSortOrder));
    }

    @Override
    public void removeByCliId(Long cliId) {
        this.remove(new LambdaQueryWrapper<AgentCliRecallTag>()
                .eq(AgentCliRecallTag::getCliId, cliId));
    }
}
