package com.ai.foundation.biz.conversation;

import com.ai.foundation.dal.entity.AgentConversationInfo;
import com.ai.foundation.dal.mapper.AgentConversationInfoMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class AgentConversationServiceImpl extends ServiceImpl<AgentConversationInfoMapper, AgentConversationInfo>
        implements AgentConversationService {

    @Override
    public IPage<AgentConversationInfo> page(Page<AgentConversationInfo> page, Long projectId, String productCode,
                                              String title, Integer state) {
        LambdaQueryWrapper<AgentConversationInfo> wrapper = new LambdaQueryWrapper<AgentConversationInfo>()
                .eq(projectId != null, AgentConversationInfo::getProjectId, projectId)
                .eq(productCode != null && !productCode.isBlank(), AgentConversationInfo::getProductCode, productCode)
                .like(title != null && !title.isBlank(), AgentConversationInfo::getTitle, title)
                .eq(state != null, AgentConversationInfo::getState, state)
                .orderByDesc(AgentConversationInfo::getIsPin)
                .orderByDesc(AgentConversationInfo::getLastMessageTime)
                .orderByDesc(AgentConversationInfo::getId);
        return this.page(page, wrapper);
    }

    @Override
    public AgentConversationInfo getByCode(String conversationCode) {
        return this.getOne(new LambdaQueryWrapper<AgentConversationInfo>()
                .eq(AgentConversationInfo::getConversationCode, conversationCode)
                .last("limit 1"));
    }
}
