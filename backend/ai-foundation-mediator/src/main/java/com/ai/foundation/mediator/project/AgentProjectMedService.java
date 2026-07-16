package com.ai.foundation.mediator.project;

import com.ai.foundation.biz.converter.ProjectConverter;
import com.ai.foundation.biz.project.AgentProjectService;
import com.ai.foundation.com.constant.CommonConstants;
import com.ai.foundation.com.exception.BusinessException;
import com.ai.foundation.com.response.PageResult;
import com.ai.foundation.com.response.ResultCode;
import com.ai.foundation.dal.entity.AgentProject;
import com.ai.foundation.facade.dto.project.AgentProjectDTO;
import com.ai.foundation.facade.dto.project.AgentProjectPageRequest;
import com.ai.foundation.facade.dto.project.AgentProjectSaveRequest;
import com.ai.foundation.mediator.prompt.ProjectPromptService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentProjectMedService {

    private final AgentProjectService projectService;
    private final ProjectConverter converter;
    private final ProjectPromptService projectPromptService;

    public PageResult<AgentProjectDTO> page(AgentProjectPageRequest request) {
        Page<AgentProject> page = new Page<>(request.getCurrent(), request.getSize());
        IPage<AgentProject> result = projectService.page(page,
                request.getProjectName(), request.getProjectCode(), request.getState());
        List<AgentProjectDTO> records = result.getRecords().stream().map(converter::toDto).toList();
        return new PageResult<>(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    public AgentProjectDTO detail(Long id) {
        AgentProject project = projectService.getById(id);
        if (project == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "项目不存在");
        }
        return converter.toDto(project);
    }

    public void create(AgentProjectSaveRequest request, String operator) {
        projectPromptService.validateProjectConfig(request.getPromptVariables());
        if (projectService.existsByCode(request.getProjectCode(), null)) {
            throw new BusinessException(ResultCode.DATA_DUPLICATED, "项目编码已存在");
        }
        AgentProject entity = converter.toEntity(request);
        if (entity.getState() == null) {
            entity.setState(CommonConstants.STATE_ENABLED);
        }
        entity.setCreateUser(operator);
        entity.setModifyUser(operator);
        projectService.save(entity);
        log.info("创建项目成功 code={} operator={}", request.getProjectCode(), operator);
    }

    public void update(AgentProjectSaveRequest request, String operator) {
        projectPromptService.validateProjectConfig(request.getPromptVariables());
        AgentProject existing = projectService.getById(request.getId());
        if (existing == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "项目不存在");
        }
        if (projectService.existsByCode(request.getProjectCode(), request.getId())) {
            throw new BusinessException(ResultCode.DATA_DUPLICATED, "项目编码已存在");
        }
        converter.updateEntity(request, existing);
        existing.setModifyUser(operator);
        projectService.updateById(existing);
        log.info("更新项目成功 id={} operator={}", request.getId(), operator);
    }

    public void delete(Long id, String operator) {
        AgentProject existing = projectService.getById(id);
        if (existing == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "项目不存在");
        }
        projectService.removeById(id);
        log.info("删除项目 id={} operator={}", id, operator);
    }
}
