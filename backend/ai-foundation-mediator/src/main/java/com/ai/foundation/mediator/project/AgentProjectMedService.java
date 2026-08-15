package com.ai.foundation.mediator.project;

import com.ai.foundation.biz.cli.AgentCliCommandService;
import com.ai.foundation.biz.cli.AgentProjectCliMappingService;
import com.ai.foundation.biz.converter.ProjectConverter;
import com.ai.foundation.biz.project.AgentProjectService;
import com.ai.foundation.com.constant.CommonConstants;
import com.ai.foundation.com.exception.BusinessException;
import com.ai.foundation.com.response.PageResult;
import com.ai.foundation.com.response.ResultCode;
import com.ai.foundation.dal.entity.AgentCliCommand;
import com.ai.foundation.dal.entity.AgentProject;
import com.ai.foundation.facade.dto.cli.BindCapabilityOptionDTO;
import com.ai.foundation.facade.dto.cli.BindOptionsResponse;
import com.ai.foundation.facade.dto.project.AgentProjectDTO;
import com.ai.foundation.facade.dto.project.AgentProjectPageRequest;
import com.ai.foundation.facade.dto.project.AgentProjectSaveRequest;
import com.ai.foundation.mediator.prompt.ProjectPromptService;
import com.ai.foundation.mediator.store.CapabilityCatalogCache;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentProjectMedService {

    private final AgentProjectService projectService;
    private final ProjectConverter converter;
    private final ProjectPromptService projectPromptService;
    private final AgentCliCommandService cliCommandService;
    private final AgentProjectCliMappingService projectCliMappingService;
    private final CapabilityCatalogCache capabilityCatalogCache;

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
        if (StringUtils.isBlank(entity.getPromptVariables())) {
            entity.setPromptVariables(null);
        }
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
        if (StringUtils.isBlank(existing.getPromptVariables())) {
            existing.setPromptVariables(null);
        }
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

    public BindOptionsResponse listBindOptions(Long projectId) {
        AgentProject project = projectService.getById(projectId);
        if (project == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "项目不存在");
        }
        List<AgentCliCommand> allClis = cliCommandService.list(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AgentCliCommand>()
                        .eq(AgentCliCommand::getState, CommonConstants.STATE_ENABLED)
                        .orderByAsc(AgentCliCommand::getCommandName));
        Set<Long> boundIds = new HashSet<>(projectCliMappingService.listCliIdsByProjectId(projectId));
        List<BindCapabilityOptionDTO> cliOptions = allClis.stream()
                .map(cli -> {
                    BindCapabilityOptionDTO dto = new BindCapabilityOptionDTO();
                    dto.setId(cli.getId());
                    dto.setCommandName(cli.getCommandName());
                    dto.setCommandType(cli.getCommandType());
                    dto.setDescription(cli.getDescription());
                    dto.setBound(boundIds.contains(cli.getId()));
                    return dto;
                })
                .toList();
        BindOptionsResponse response = new BindOptionsResponse();
        response.setCliOptions(cliOptions);
        return response;
    }

    @Transactional(rollbackFor = Exception.class)
    public void bindCapabilities(Long projectId, List<Long> cliIds, String operator) {
        AgentProject project = projectService.getById(projectId);
        if (project == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "项目不存在");
        }
        projectCliMappingService.removeByProjectId(projectId);
        if (cliIds != null && !cliIds.isEmpty()) {
            List<com.ai.foundation.dal.entity.AgentProjectCliMapping> mappings = new ArrayList<>();
            for (Long cliId : cliIds) {
                com.ai.foundation.dal.entity.AgentProjectCliMapping mapping =
                        new com.ai.foundation.dal.entity.AgentProjectCliMapping();
                mapping.setProjectId(projectId);
                mapping.setCliId(cliId);
                mapping.setState(CommonConstants.STATE_ENABLED);
                mapping.setCreateUser(operator);
                mapping.setModifyUser(operator);
                mappings.add(mapping);
            }
            projectCliMappingService.saveBatch(mappings);
        }
        capabilityCatalogCache.invalidate(project.getProjectCode());
        log.info("项目挂载能力更新 projectId={} cliCount={}", projectId, cliIds == null ? 0 : cliIds.size());
    }
}
