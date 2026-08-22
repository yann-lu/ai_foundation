package com.ai.foundation.biz.skill;

import com.ai.foundation.com.prompt.RuntimeContextTemplateRenderer;
import com.ai.foundation.dal.entity.AgentSkillDefinition;
import com.ai.foundation.dal.mapper.AgentSkillDefinitionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AgentSkillDefinitionServiceImpl
        extends ServiceImpl<AgentSkillDefinitionMapper, AgentSkillDefinition>
        implements AgentSkillDefinitionService {

    private final AgentProjectSkillRelService projectSkillRelService;

    @Override
    public IPage<AgentSkillDefinition> page(Page<AgentSkillDefinition> page, String keyword,
                                             String skillType, Integer state) {
        LambdaQueryWrapper<AgentSkillDefinition> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(AgentSkillDefinition::getSkillName, keyword)
                    .or().like(AgentSkillDefinition::getSkillCode, keyword));
        }
        if (skillType != null && !skillType.isBlank()) {
            wrapper.eq(AgentSkillDefinition::getSkillType, skillType);
        }
        if (state != null) {
            wrapper.eq(AgentSkillDefinition::getState, state);
        }
        wrapper.orderByDesc(AgentSkillDefinition::getCreateTime);
        return baseMapper.selectPage(page, wrapper);
    }

    @Override
    public boolean existsBySkillCode(String skillCode, Long excludeId) {
        if (skillCode == null || skillCode.isBlank()) {
            return false;
        }
        LambdaQueryWrapper<AgentSkillDefinition> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentSkillDefinition::getSkillCode, skillCode);
        if (excludeId != null) {
            wrapper.ne(AgentSkillDefinition::getId, excludeId);
        }
        return baseMapper.selectCount(wrapper) > 0;
    }

    @Override
    public List<AgentSkillDefinition> listByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return baseMapper.selectBatchIds(ids);
    }

    @Override
    public Set<String> collectUserRequiredContextKeys(Long projectId) {
        if (projectId == null) {
            return Set.of();
        }
        List<Long> skillIds = projectSkillRelService.listSkillIdsByProjectId(projectId);
        if (skillIds == null || skillIds.isEmpty()) {
            return Set.of();
        }
        List<AgentSkillDefinition> skills = listByIds(skillIds);
        if (skills == null || skills.isEmpty()) {
            return Set.of();
        }
        Set<String> required = new LinkedHashSet<>();
        for (AgentSkillDefinition skill : skills) {
            String template = skill == null ? null : skill.getRuntimeContextTemplate();
            if (template == null || template.isBlank()) {
                continue;
            }
            required.addAll(RuntimeContextTemplateRenderer.extractUserRequiredKeys(template));
        }
        return required;
    }
}
