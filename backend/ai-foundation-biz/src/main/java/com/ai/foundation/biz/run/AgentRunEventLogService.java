package com.ai.foundation.biz.run;

import com.ai.foundation.dal.entity.AgentRunEventLog;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface AgentRunEventLogService extends IService<AgentRunEventLog> {

    List<AgentRunEventLog> listByRunId(Long runId);

    void appendEvent(Long runId, Long conversationId, String eventType,
                     String taskState, String eventData, Long timestamp);
}
