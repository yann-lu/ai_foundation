package com.ai.foundation.biz.run;

import com.ai.foundation.dal.entity.AgentRunTaskInfo;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface AgentRunTaskInfoService extends IService<AgentRunTaskInfo> {

    List<AgentRunTaskInfo> listByRunId(Long runId);

    AgentRunTaskInfo createTask(Long runId, String taskType, String capabilityType,
                                 Long refId, String refName, String instruction);

    void markRunning(Long taskId);

    void markSuccess(Long taskId, String resultRef, Long costMs);

    void markFailed(Long taskId, String errorMessage, Long costMs);
}
