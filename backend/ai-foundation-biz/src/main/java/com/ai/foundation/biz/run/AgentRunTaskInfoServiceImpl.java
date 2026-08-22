package com.ai.foundation.biz.run;

import com.ai.foundation.dal.entity.AgentRunTaskInfo;
import com.ai.foundation.dal.mapper.AgentRunTaskInfoMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
public class AgentRunTaskInfoServiceImpl
        extends ServiceImpl<AgentRunTaskInfoMapper, AgentRunTaskInfo>
        implements AgentRunTaskInfoService {

    @Override
    public List<AgentRunTaskInfo> listByRunId(Long runId) {
        if (runId == null) {
            return Collections.emptyList();
        }
        return this.list(new LambdaQueryWrapper<AgentRunTaskInfo>()
                .eq(AgentRunTaskInfo::getRunId, runId)
                .orderByAsc(AgentRunTaskInfo::getId));
    }

    @Override
    public AgentRunTaskInfo createTask(Long runId, String taskType, String capabilityType,
                                        Long refId, String refName, String instruction,
                                        String inputParams) {
        AgentRunTaskInfo task = new AgentRunTaskInfo();
        task.setRunId(runId);
        task.setTaskCode("task_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 1000));
        task.setTaskType(taskType);
        task.setCapabilityType(capabilityType);
        task.setRefId(refId);
        task.setRefName(refName);
        task.setInstruction(instruction);
        task.setInputParams(inputParams);
        task.setTaskState("pending");
        this.save(task);
        return task;
    }

    @Override
    public void markRunning(Long taskId) {
        if (taskId == null) return;
        AgentRunTaskInfo update = new AgentRunTaskInfo();
        update.setId(taskId);
        update.setTaskState("running");
        this.updateById(update);
    }

    @Override
    public void markSuccess(Long taskId, String resultRef, Long costMs) {
        if (taskId == null) return;
        AgentRunTaskInfo update = new AgentRunTaskInfo();
        update.setId(taskId);
        update.setTaskState("completed");
        update.setResultRef(resultRef);
        update.setCostMs(costMs);
        this.updateById(update);
    }

    @Override
    public void markFailed(Long taskId, String errorMessage, Long costMs) {
        if (taskId == null) return;
        AgentRunTaskInfo update = new AgentRunTaskInfo();
        update.setId(taskId);
        update.setTaskState("failed");
        update.setErrorMessage(errorMessage);
        update.setCostMs(costMs);
        this.updateById(update);
    }
}
