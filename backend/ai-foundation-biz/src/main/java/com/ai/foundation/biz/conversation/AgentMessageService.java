package com.ai.foundation.biz.conversation;

import com.ai.foundation.dal.entity.AgentMessageInfo;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface AgentMessageService extends IService<AgentMessageInfo> {

    List<AgentMessageInfo> recentMessages(Long conversationId, int limit);

    List<AgentMessageInfo> scrollMessages(Long conversationId, Long beforeId, int limit);

    void softDeleteByConversationId(Long conversationId);
}
