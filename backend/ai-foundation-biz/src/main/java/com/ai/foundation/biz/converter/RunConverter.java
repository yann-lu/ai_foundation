package com.ai.foundation.biz.converter;

import com.ai.foundation.dal.entity.AgentRunEventLog;
import com.ai.foundation.dal.entity.AgentRunInfo;
import com.ai.foundation.dal.entity.AgentRunTaskInfo;
import com.ai.foundation.facade.dto.run.RunEventDTO;
import com.ai.foundation.facade.dto.run.RunItemDTO;
import com.ai.foundation.facade.dto.run.RunTaskDTO;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface RunConverter {

    RunItemDTO toItemDto(AgentRunInfo entity);

    List<RunItemDTO> toItemDtoList(List<AgentRunInfo> list);

    RunTaskDTO toTaskDto(AgentRunTaskInfo entity);

    List<RunTaskDTO> toTaskDtoList(List<AgentRunTaskInfo> list);

    RunEventDTO toEventDto(AgentRunEventLog entity);

    List<RunEventDTO> toEventDtoList(List<AgentRunEventLog> list);
}
