package com.ai.foundation.biz.cli;

import com.ai.foundation.dal.entity.AgentProjectCliMapping;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface AgentProjectCliMappingService extends IService<AgentProjectCliMapping> {

    List<AgentProjectCliMapping> listByProjectId(Long projectId);

    List<Long> listCliIdsByProjectId(Long projectId);

    void removeByProjectId(Long projectId);

    void removeByCliId(Long cliId);

    int countByCliId(Long cliId);

    List<Long> listProjectIdsByCliId(Long cliId);
}
