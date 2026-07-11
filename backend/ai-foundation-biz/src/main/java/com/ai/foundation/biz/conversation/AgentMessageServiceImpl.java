package com.ai.foundation.biz.conversation;

import com.ai.foundation.dal.entity.AgentMessageInfo;
import com.ai.foundation.dal.mapper.AgentMessageInfoMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AgentMessageServiceImpl extends ServiceImpl<AgentMessageInfoMapper, AgentMessageInfo>
        implements AgentMessageService {

    @Override
    public List<AgentMessageInfo> recentMessages(Long conversationId, int limit) {
        return this.list(new LambdaQueryWrapper<AgentMessageInfo>()
                .eq(AgentMessageInfo::getConversationId, conversationId)
                .eq(AgentMessageInfo::getState, 1)
                .orderByDesc(AgentMessageInfo::getId)
                .last("limit " + limit));
    }

    @Override
    public List<AgentMessageInfo> scrollMessages(Long conversationId, Long beforeId, int limit) {
        LambdaQueryWrapper<AgentMessageInfo> wrapper = new LambdaQueryWrapper<AgentMessageInfo>()
                .eq(AgentMessageInfo::getConversationId, conversationId)
                .eq(AgentMessageInfo::getState, 1)
                .lt(beforeId != null, AgentMessageInfo::getId, beforeId)
                .orderByDesc(AgentMessageInfo::getId)
                .last("limit " + limit);
        return this.list(wrapper);
    }

    @Override
    public void softDeleteByConversationId(Long conversationId) {
        this.update(new LambdaUpdateWrapper<AgentMessageInfo>()
                .eq(AgentMessageInfo::getConversationId, conversationId)
                .set(AgentMessageInfo::getIsDelete, 1));
    }
}
