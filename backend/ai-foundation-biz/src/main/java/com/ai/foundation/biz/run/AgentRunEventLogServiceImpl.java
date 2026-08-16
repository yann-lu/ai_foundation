package com.ai.foundation.biz.run;

import com.ai.foundation.dal.entity.AgentRunEventLog;
import com.ai.foundation.dal.mapper.AgentRunEventLogMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class AgentRunEventLogServiceImpl
        extends ServiceImpl<AgentRunEventLogMapper, AgentRunEventLog>
        implements AgentRunEventLogService {

    @Override
    public List<AgentRunEventLog> listByRunId(Long runId) {
        if (runId == null) {
            return Collections.emptyList();
        }
        return this.list(new LambdaQueryWrapper<AgentRunEventLog>()
                .eq(AgentRunEventLog::getRunId, runId)
                .orderByAsc(AgentRunEventLog::getSeqNo, AgentRunEventLog::getId));
    }

    @Override
    public void appendEvent(Long runId, Long conversationId, String eventType,
                            String taskState, String eventData, Long timestamp) {
        if (runId == null || eventType == null) {
            return;
        }
        try {
            AgentRunEventLog event = new AgentRunEventLog();
            event.setRunId(runId);
            event.setConversationId(conversationId != null ? conversationId : 0L);
            event.setEventType(eventType);
            event.setTaskState(taskState);
            event.setEventData(eventData);
            event.setEventTimestamp(timestamp != null ? timestamp : System.currentTimeMillis());
            event.setSeqNo(0); // 简化：按 ID 排序即可，seq_no 预留
            this.save(event);
        } catch (Exception ex) {
            log.warn("保存运行事件日志失败 runId={} eventType={}", runId, eventType, ex);
        }
    }
}
