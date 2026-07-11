package com.ai.foundation.biz.project;

import com.ai.foundation.dal.entity.AgentProject;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

public interface AgentProjectService extends IService<AgentProject> {

    IPage<AgentProject> page(Page<AgentProject> page, String projectName, String projectCode, Integer state);

    AgentProject getByCode(String projectCode);

    boolean existsByCode(String projectCode, Long excludeId);
}
