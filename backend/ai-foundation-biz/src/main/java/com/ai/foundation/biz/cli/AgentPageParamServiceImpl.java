package com.ai.foundation.biz.cli;

import com.ai.foundation.dal.entity.AgentPageParam;
import com.ai.foundation.dal.mapper.AgentPageParamMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AgentPageParamServiceImpl extends ServiceImpl<AgentPageParamMapper, AgentPageParam>
        implements AgentPageParamService {

    @Override
    public List<AgentPageParam> listByPageId(Long pageId) {
        return this.list(new LambdaQueryWrapper<AgentPageParam>()
                .eq(AgentPageParam::getPageId, pageId)
                .orderByAsc(AgentPageParam::getId));
    }

    @Override
    public void removeByPageId(Long pageId) {
        this.remove(new LambdaQueryWrapper<AgentPageParam>()
                .eq(AgentPageParam::getPageId, pageId));
    }
}
