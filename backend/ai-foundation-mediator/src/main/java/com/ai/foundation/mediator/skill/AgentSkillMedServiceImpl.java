package com.ai.foundation.mediator.skill;

import com.ai.foundation.biz.converter.SkillConverter;
import com.ai.foundation.biz.project.AgentProjectService;
import com.ai.foundation.biz.skill.AgentProjectSkillRelService;
import com.ai.foundation.biz.skill.AgentSkillDefinitionService;
import com.ai.foundation.biz.skill.AgentSkillResourceService;
import com.ai.foundation.com.constant.CommonConstants;
import com.ai.foundation.com.exception.BusinessException;
import com.ai.foundation.com.response.PageResult;
import com.ai.foundation.com.response.ResultCode;
import com.ai.foundation.dal.entity.AgentProject;
import com.ai.foundation.dal.entity.AgentProjectSkillRel;
import com.ai.foundation.dal.entity.AgentSkillDefinition;
import com.ai.foundation.dal.entity.AgentSkillResource;
import com.ai.foundation.facade.dto.skill.AgentSkillDTO;
import com.ai.foundation.facade.dto.skill.AgentSkillPageRequest;
import com.ai.foundation.facade.dto.skill.AgentSkillSaveRequest;
import com.ai.foundation.facade.dto.skill.SkillBindOptionDTO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentSkillMedServiceImpl implements AgentSkillMedService {

    private static final String RESOURCE_TYPE_CLI = "CLI";

    private final AgentSkillDefinitionService skillDefinitionService;
    private final AgentSkillResourceService skillResourceService;
    private final AgentProjectSkillRelService projectSkillRelService;
    private final AgentProjectService projectService;
    private final SkillConverter skillConverter;

    @Override
    public PageResult<AgentSkillDTO> page(AgentSkillPageRequest request) {
        Page<AgentSkillDefinition> page = new Page<>(request.getCurrent(), request.getSize());
        var result = skillDefinitionService.page(page, request.getKeyword(),
                request.getSkillType(), request.getState());
        List<AgentSkillDTO> records = result.getRecords().stream()
                .map(skillConverter::toDto)
                .toList();
        return new PageResult<>(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public AgentSkillDTO detail(Long id) {
        AgentSkillDefinition skill = skillDefinitionService.getById(id);
        if (skill == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "技能不存在");
        }
        AgentSkillDTO dto = skillConverter.toDto(skill);
        List<Long> cliIds = skillResourceService.listResourceIdsBySkillIdAndType(id, RESOURCE_TYPE_CLI);
        dto.setCliIds(cliIds);
        return dto;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(AgentSkillSaveRequest request, String operator) {
        if (skillDefinitionService.existsBySkillCode(request.getSkillCode(), request.getId())) {
            throw new BusinessException(ResultCode.DATA_DUPLICATED, "技能编码已存在");
        }

        AgentSkillDefinition skill;
        if (request.getId() == null) {
            skill = skillConverter.toEntity(request);
            if (StringUtils.isBlank(skill.getConfigJson())) {
                skill.setConfigJson("{}");
            }
            if (skill.getState() == null) {
                skill.setState(CommonConstants.STATE_ENABLED);
            }
            skill.setCreateUser(operator);
            skill.setModifyUser(operator);
            skillDefinitionService.save(skill);
            log.info("创建技能成功 code={} operator={}", request.getSkillCode(), operator);
        } else {
            AgentSkillDefinition existing = skillDefinitionService.getById(request.getId());
            if (existing == null) {
                throw new BusinessException(ResultCode.DATA_NOT_FOUND, "技能不存在");
            }
            skillConverter.updateEntity(request, existing);
            if (StringUtils.isBlank(existing.getConfigJson())) {
                existing.setConfigJson("{}");
            }
            existing.setModifyUser(operator);
            skillDefinitionService.updateById(existing);
            skillResourceService.removeBySkillId(request.getId());
            log.info("更新技能成功 id={} operator={}", request.getId(), operator);
            skill = existing;
        }

        saveSkillResources(skill.getId(), request.getCliIds(), operator);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, String operator) {
        AgentSkillDefinition skill = skillDefinitionService.getById(id);
        if (skill == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "技能不存在");
        }
        long projectCount = projectSkillRelService.countBySkillId(id);
        if (projectCount > 0) {
            throw new BusinessException(ResultCode.STATE_INVALID,
                    "该技能已被 " + projectCount + " 个项目挂载，请先解除挂载");
        }
        skillDefinitionService.removeById(id);
        skillResourceService.removeBySkillId(id);
        log.info("删除技能 id={} operator={}", id, operator);
    }

    @Override
    public List<SkillBindOptionDTO> listBindOptions(Long projectId) {
        AgentProject project = projectService.getById(projectId);
        if (project == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "项目不存在");
        }
        List<AgentSkillDefinition> allSkills = skillDefinitionService.lambdaQuery()
                .eq(AgentSkillDefinition::getState, CommonConstants.STATE_ENABLED)
                .orderByAsc(AgentSkillDefinition::getSkillName)
                .list();
        Set<Long> boundIds = new HashSet<>(projectSkillRelService.listSkillIdsByProjectId(projectId));
        return allSkills.stream()
                .map(skill -> {
                    SkillBindOptionDTO dto = new SkillBindOptionDTO();
                    dto.setId(skill.getId());
                    dto.setSkillName(skill.getSkillName());
                    dto.setSkillType(skill.getSkillType());
                    dto.setDescription(skill.getDescription());
                    dto.setBound(boundIds.contains(skill.getId()));
                    return dto;
                })
                .toList();
    }

    private void saveSkillResources(Long skillId, List<Long> cliIds, String operator) {
        if (cliIds == null || cliIds.isEmpty()) {
            return;
        }
        List<AgentSkillResource> resources = new ArrayList<>();
        int sortOrder = 0;
        for (Long cliId : cliIds) {
            AgentSkillResource resource = new AgentSkillResource();
            resource.setSkillId(skillId);
            resource.setResourceType(RESOURCE_TYPE_CLI);
            resource.setResourceId(cliId);
            resource.setSortOrder(sortOrder++);
            resource.setCreateUser(operator);
            resource.setModifyUser(operator);
            resources.add(resource);
        }
        skillResourceService.saveBatch(resources);
    }
}
