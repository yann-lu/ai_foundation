package com.ai.foundation.biz.run;

import com.ai.foundation.dal.entity.AgentRun;
import com.baomidou.mybatisplus.extension.service.IService;

public interface AgentRunService extends IService<AgentRun> {

    AgentRun getByRunCode(String runCode);

    AgentRun getLatestByConversationId(Long conversationId);
}
