package com.ai.foundation.biz.run;

import com.ai.foundation.dal.entity.AgentRunInfo;
import com.ai.foundation.dal.mapper.AgentRunInfoMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class AgentRunInfoServiceImpl extends ServiceImpl<AgentRunInfoMapper, AgentRunInfo>
        implements AgentRunInfoService {

    @Override
    public AgentRunInfo getByRunCode(String runCode) {
        if (runCode == null || runCode.isBlank()) {
            return null;
        }
        return this.getOne(new LambdaQueryWrapper<AgentRunInfo>()
                .eq(AgentRunInfo::getRunCode, runCode.trim())
                .last("limit 1"));
    }

    @Override
    public AgentRunInfo getLatestByConversationId(Long conversationId) {
        if (conversationId == null) {
            return null;
        }
        return this.getOne(new LambdaQueryWrapper<AgentRunInfo>()
                .eq(AgentRunInfo::getConversationId, conversationId)
                .eq(AgentRunInfo::getState, 1)
                .orderByDesc(AgentRunInfo::getId)
                .last("limit 1"));
    }

    @Override
    public com.baomidou.mybatisplus.core.metadata.IPage<AgentRunInfo> pageByConversationId(
            Long conversationId, long current, long size) {
        if (conversationId == null) {
            return new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(current, size);
        }
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<AgentRunInfo> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(current, size);
        return this.page(page, new LambdaQueryWrapper<AgentRunInfo>()
                .eq(AgentRunInfo::getConversationId, conversationId)
                .eq(AgentRunInfo::getState, 1)
                .orderByDesc(AgentRunInfo::getId));
    }
}
