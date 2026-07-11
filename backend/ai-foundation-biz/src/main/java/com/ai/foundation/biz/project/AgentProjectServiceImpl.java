package com.ai.foundation.biz.project;

import com.ai.foundation.dal.entity.AgentProject;
import com.ai.foundation.dal.mapper.AgentProjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class AgentProjectServiceImpl extends ServiceImpl<AgentProjectMapper, AgentProject>
        implements AgentProjectService {

    @Override
    public IPage<AgentProject> page(Page<AgentProject> page, String projectName, String projectCode, Integer state) {
        LambdaQueryWrapper<AgentProject> wrapper = new LambdaQueryWrapper<AgentProject>()
                .like(projectName != null && !projectName.isBlank(), AgentProject::getProjectName, projectName)
                .eq(projectCode != null && !projectCode.isBlank(), AgentProject::getProjectCode, projectCode)
                .eq(state != null, AgentProject::getState, state)
                .orderByDesc(AgentProject::getUpdateTime);
        return this.page(page, wrapper);
    }

    @Override
    public AgentProject getByCode(String projectCode) {
        return this.getOne(new LambdaQueryWrapper<AgentProject>()
                .eq(AgentProject::getProjectCode, projectCode)
                .last("limit 1"));
    }

    @Override
    public boolean existsByCode(String projectCode, Long excludeId) {
        LambdaQueryWrapper<AgentProject> wrapper = new LambdaQueryWrapper<AgentProject>()
                .eq(AgentProject::getProjectCode, projectCode)
                .ne(excludeId != null, AgentProject::getId, excludeId);
        return this.count(wrapper) > 0;
    }
}
