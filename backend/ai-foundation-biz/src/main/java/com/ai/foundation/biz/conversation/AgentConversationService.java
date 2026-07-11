package com.ai.foundation.biz.conversation;

import com.ai.foundation.dal.entity.AgentConversationInfo;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

public interface AgentConversationService extends IService<AgentConversationInfo> {

    IPage<AgentConversationInfo> page(Page<AgentConversationInfo> page, Long projectId, String productCode,
                                       String title, Integer state);

    AgentConversationInfo getByCode(String conversationCode);
}
