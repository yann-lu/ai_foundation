package com.ai.foundation.biz.run;

import com.ai.foundation.dal.entity.AgentRunEventLog;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface AgentRunEventLogService extends IService<AgentRunEventLog> {

    List<AgentRunEventLog> listByRunId(Long runId);

    /**
     * 拉取指定会话下的所有 Run 事件（跨 Run），按 run_id asc + id asc 排序，
     * 供前端轨迹页拼接多轮对话。
     */
    List<AgentRunEventLog> listByConversationId(Long conversationId);

    void appendEvent(Long runId, Long conversationId, String eventType,
                     String taskState, String eventData, Long timestamp);
}
