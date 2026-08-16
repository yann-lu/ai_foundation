package com.ai.foundation.biz.run;

import com.ai.foundation.dal.entity.AgentRunInfo;
import com.baomidou.mybatisplus.extension.service.IService;

public interface AgentRunInfoService extends IService<AgentRunInfo> {

    AgentRunInfo getByRunCode(String runCode);

    AgentRunInfo getLatestByConversationId(Long conversationId);
}
