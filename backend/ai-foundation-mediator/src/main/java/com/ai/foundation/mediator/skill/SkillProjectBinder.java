package com.ai.foundation.mediator.skill;

import com.ai.foundation.biz.project.AgentProjectService;
import com.ai.foundation.biz.skill.AgentProjectSkillRelService;
import com.ai.foundation.com.constant.CommonConstants;
import com.ai.foundation.com.exception.BusinessException;
import com.ai.foundation.com.response.ResultCode;
import com.ai.foundation.dal.entity.AgentProject;
import com.ai.foundation.dal.entity.AgentProjectSkillRel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class SkillProjectBinder {

    private final AgentProjectService projectService;
    private final AgentProjectSkillRelService projectSkillRelService;

    @Transactional(rollbackFor = Exception.class)
    public void bindSkills(Long projectId, List<Long> skillIds, String operator) {
        AgentProject project = projectService.getById(projectId);
        if (project == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "项目不存在");
        }
        Set<Long> newSkillIds = skillIds == null ? new HashSet<>() : new HashSet<>(skillIds);
        Set<Long> existingSkillIds = new HashSet<>(projectSkillRelService.listSkillIdsByProjectId(projectId));

        Set<Long> toRemove = new HashSet<>(existingSkillIds);
        toRemove.removeAll(newSkillIds);
        if (!toRemove.isEmpty()) {
            projectSkillRelService.removeByProjectIdAndSkillIds(projectId, new ArrayList<>(toRemove));
        }

        Set<Long> toAdd = new HashSet<>(newSkillIds);
        toAdd.removeAll(existingSkillIds);
        if (!toAdd.isEmpty()) {
            List<AgentProjectSkillRel> relations = new ArrayList<>();
            for (Long skillId : toAdd) {
                AgentProjectSkillRel rel = new AgentProjectSkillRel();
                rel.setProjectId(projectId);
                rel.setSkillId(skillId);
                rel.setState(CommonConstants.STATE_ENABLED);
                rel.setCreateUser(operator);
                rel.setModifyUser(operator);
                relations.add(rel);
            }
            projectSkillRelService.saveBatch(relations);
        }
        log.info("项目技能挂载更新 projectId={} add={} remove={}", projectId, toAdd.size(), toRemove.size());
    }

    public List<Long> listSkillIdsByProjectId(Long projectId) {
        return projectSkillRelService.listSkillIdsByProjectId(projectId);
    }
}
