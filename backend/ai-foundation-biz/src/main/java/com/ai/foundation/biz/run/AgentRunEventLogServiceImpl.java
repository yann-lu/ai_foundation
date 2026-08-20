package com.ai.foundation.biz.run;

import com.ai.foundation.dal.entity.AgentRunEventLog;
import com.ai.foundation.dal.mapper.AgentRunEventLogMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class AgentRunEventLogServiceImpl
        extends ServiceImpl<AgentRunEventLogMapper, AgentRunEventLog>
        implements AgentRunEventLogService {

    /**
     * 每个 runId 一个自增序号生成器。新建 run 首次 append 时初始化为 0，
     * 之后每个事件 +1，保证事件按写入顺序得到连续 seq_no。
     */
    private final ConcurrentMap<Long, AtomicInteger> seqCounters = new ConcurrentHashMap<>();

    @Override
    public List<AgentRunEventLog> listByRunId(Long runId) {
        if (runId == null) {
            return Collections.emptyList();
        }
        return this.list(new LambdaQueryWrapper<AgentRunEventLog>()
                .eq(AgentRunEventLog::getRunId, runId)
                .orderByAsc(AgentRunEventLog::getId));
    }

    @Override
    public List<AgentRunEventLog> listByConversationId(Long conversationId) {
        if (conversationId == null) {
            return Collections.emptyList();
        }
        return this.list(new LambdaQueryWrapper<AgentRunEventLog>()
                .eq(AgentRunEventLog::getConversationId, conversationId)
                .orderByAsc(AgentRunEventLog::getRunId, AgentRunEventLog::getId));
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
            event.setSeqNo(nextSeqNo(runId));
            this.save(event);
        } catch (Exception ex) {
            log.warn("保存运行事件日志失败 runId={} eventType={}", runId, eventType, ex);
        }
    }

    private int nextSeqNo(Long runId) {
        AtomicInteger counter = seqCounters.computeIfAbsent(runId, k -> new AtomicInteger(0));
        return counter.incrementAndGet();
    }
}
